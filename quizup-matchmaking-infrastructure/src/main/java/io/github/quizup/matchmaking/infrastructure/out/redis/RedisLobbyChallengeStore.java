package io.github.quizup.matchmaking.infrastructure.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.model.SessionTimer;
import io.github.quizup.matchmaking.domain.port.out.ChallengeStorePort;
import io.github.quizup.matchmaking.domain.port.out.LobbyStorePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Store chaud Redis des salons et défis : hash + TTL, transitions atomiques (Lua) avec
 * événement d'outbox écrit dans la même opération. Les timers (expiration du salon, ready-check,
 * expiration du défi) vivent dans le ZSET partagé du session tier.
 */
@Component
public class RedisLobbyChallengeStore implements LobbyStorePort, ChallengeStorePort {

    private static final String LOBBY_PREFIX = "session:lobby:";
    private static final String LOBBY_INDEX_PREFIX = "session:lobby:idx:";
    private static final String CHALLENGE_PREFIX = "session:challenge:";
    private static final String CHALLENGE_INDEX_PREFIX = "session:challenge:idx:";
    private static final String OUTBOX_KEY = "session:outbox";
    private static final String SEQ_PREFIX = "session:seq:";
    private static final String TIMERS_KEY = "session:timers";
    private static final Duration INDEX_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLobbyChallengeStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ============================ LobbyStorePort ============================

    @Override
    public void create(Lobby lobby, LobbyEvent.LobbyCreatedEvent event) {
        long ttlMs = Duration.between(Instant.now(), lobby.expiresAt()).toMillis();
        redis.execute(LOBBY_CREATE,
                List.of(lobbyKey(lobby.lobbyId()), TIMERS_KEY, OUTBOX_KEY, seqKey(lobby.lobbyId())),
                lobby.lobbyId(), lobby.topicId(), lobby.initiatorId(), orEmpty(lobby.opponentId()),
                epochMillis(lobby.expiresAt()), epochMillis(lobby.createdAt()),
                String.valueOf(Math.max(ttlMs, 1000L)),
                timerMember(SessionTimer.LOBBY_EXPIRY, lobby.lobbyId()),
                event.getClass().getName(), json(event));
        addIndex(LOBBY_INDEX_PREFIX, lobby.initiatorId(), lobby.lobbyId());
        if (lobby.opponentId() != null && !lobby.opponentId().isBlank()) {
            addIndex(LOBBY_INDEX_PREFIX, lobby.opponentId(), lobby.lobbyId());
        }
    }

    @Override
    public Optional<Lobby> findById(String lobbyId) {
        Map<Object, Object> hash = redis.opsForHash().entries(lobbyKey(lobbyId));
        return hash.isEmpty() ? Optional.empty() : Optional.of(toLobby(hash));
    }

    @Override
    public List<Lobby> findOpenByPlayerId(String playerId) {
        return idsOf(LOBBY_INDEX_PREFIX, playerId).stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .filter(lobby -> lobby.status() == LobbyStatus.CREATED)
                .toList();
    }

    @Override
    public JoinOutcome join(String lobbyId, String playerId, LobbyEvent.LobbyJoinedEvent event) {
        Long result = redis.execute(JOIN,
                List.of(lobbyKey(lobbyId), OUTBOX_KEY, seqKey(lobbyId)),
                playerId, epochMillis(event.joinedAt()),
                event.getClass().getName(), json(event));
        return switch (result == null ? 0 : result.intValue()) {
            case 1 -> {
                addIndex(LOBBY_INDEX_PREFIX, playerId, lobbyId);
                yield JoinOutcome.JOINED;
            }
            case 2 -> JoinOutcome.IDEMPOTENT;
            case 3 -> JoinOutcome.FULL;
            case 4 -> JoinOutcome.NOT_INVITED;
            default -> JoinOutcome.CLOSED;
        };
    }

    @Override
    public EnterOutcome enter(String lobbyId,
                              String playerId,
                              LobbyEvent.LobbyRoomEnteredEvent enteredEvent,
                              LobbyEvent.LobbyAllPlayersPresentEvent allPresentEvent) {
        Long result = redis.execute(ENTER,
                List.of(lobbyKey(lobbyId), TIMERS_KEY, OUTBOX_KEY, seqKey(lobbyId)),
                playerId, epochMillis(enteredEvent.enteredAt()),
                epochMillis(allPresentEvent.readyDeadlineAt()),
                timerMember(SessionTimer.LOBBY_READY_CHECK, lobbyId),
                enteredEvent.getClass().getName(), json(enteredEvent),
                allPresentEvent.getClass().getName(), json(allPresentEvent));
        return switch (result == null ? 0 : result.intValue()) {
            case 1 -> EnterOutcome.ENTERED;
            case 2 -> EnterOutcome.IDEMPOTENT;
            case 3 -> EnterOutcome.BOTH_PRESENT;
            case -1 -> EnterOutcome.NOT_PARTICIPANT;
            default -> EnterOutcome.CLOSED;
        };
    }

    @Override
    public LeaveOutcome leave(String lobbyId, String playerId, LobbyEvent.LobbyLeftEvent event) {
        Long result = redis.execute(LEAVE,
                List.of(lobbyKey(lobbyId), OUTBOX_KEY, seqKey(lobbyId)),
                playerId, epochMillis(event.leftAt()),
                event.getClass().getName(), json(event));
        return switch (result == null ? 0 : result.intValue()) {
            case 1 -> LeaveOutcome.LEFT;
            case 2 -> LeaveOutcome.IDEMPOTENT;
            case -1 -> LeaveOutcome.NOT_PARTICIPANT;
            default -> LeaveOutcome.CLOSED;
        };
    }

    @Override
    public boolean complete(String lobbyId, String gameId, LobbyEvent.LobbyCompletedEvent event) {
        return terminal(lobbyKey(lobbyId), "CREATED", "CLOSED", "updatedAt",
                epochMillis(event.completedAt()), LobbyDeadline.LOBBY_RETENTION_DURATION.toMillis(),
                "gameId", gameId, "", "",
                "initiatorPresent", "1", "participantPresent", "1",
                event.getClass().getName(), json(event), lobbyId);
    }

    @Override
    public boolean fail(String lobbyId, String reason, LobbyEvent.LobbyFailedEvent event) {
        return terminal(lobbyKey(lobbyId), "CREATED", "FAILED", "updatedAt",
                epochMillis(event.failedAt()), LobbyDeadline.LOBBY_RETENTION_DURATION.toMillis(),
                "failureReason", reason, "", "", "", "", "", "",
                event.getClass().getName(), json(event), lobbyId);
    }

    @Override
    public boolean miss(String lobbyId, String absentPlayerId, String reason, LobbyEvent.LobbyMissedEvent event) {
        return terminal(lobbyKey(lobbyId), "CREATED", "CLOSED", "updatedAt",
                epochMillis(event.missedAt()), LobbyDeadline.LOBBY_RETENTION_DURATION.toMillis(),
                "missedReason", reason, "", "", "", "", "", "",
                event.getClass().getName(), json(event), lobbyId);
    }

    @Override
    public boolean cancel(String lobbyId, LobbyEvent.LobbyCancelledEvent event) {
        return terminal(lobbyKey(lobbyId), "CREATED", "CLOSED", "updatedAt",
                epochMillis(event.cancelledAt()), LobbyDeadline.LOBBY_RETENTION_DURATION.toMillis(),
                "", "", "", "", "", "", "", "",
                event.getClass().getName(), json(event), lobbyId);
    }

    @Override
    public boolean decline(String lobbyId, LobbyEvent.LobbyDeclinedEvent event) {
        return terminal(lobbyKey(lobbyId), "CREATED", "CLOSED", "updatedAt",
                epochMillis(event.declinedAt()), LobbyDeadline.LOBBY_RETENTION_DURATION.toMillis(),
                "", "", "", "", "", "", "", "",
                event.getClass().getName(), json(event), lobbyId);
    }

    @Override
    public boolean expire(String lobbyId, LobbyEvent.LobbyExpiredEvent event) {
        return terminal(lobbyKey(lobbyId), "CREATED", "CLOSED", "updatedAt",
                epochMillis(event.expiredAt()), LobbyDeadline.LOBBY_RETENTION_DURATION.toMillis(),
                "", "", "", "", "", "", "", "",
                event.getClass().getName(), json(event), lobbyId);
    }

    // ============================ ChallengeStorePort ============================

    @Override
    public void create(Challenge challenge, ChallengeEvent.ChallengeCreatedEvent event) {
        long ttlMs = Duration.between(Instant.now(), challenge.expiresAt()).toMillis();
        redis.execute(CHALLENGE_CREATE,
                List.of(challengeKey(challenge.challengeId()), TIMERS_KEY, OUTBOX_KEY,
                        seqKey(challenge.challengeId())),
                challenge.challengeId(), challenge.topicId(), challenge.challengerId(),
                challenge.opponentId(), epochMillis(challenge.expiresAt()),
                epochMillis(challenge.createdAt()), String.valueOf(Math.max(ttlMs, 1000L)),
                timerMember(SessionTimer.CHALLENGE_EXPIRY, challenge.challengeId()),
                event.getClass().getName(), json(event));
        addIndex(CHALLENGE_INDEX_PREFIX, challenge.challengerId(), challenge.challengeId());
        addIndex(CHALLENGE_INDEX_PREFIX, challenge.opponentId(), challenge.challengeId());
    }

    @Override
    public Optional<Challenge> findChallengeById(String challengeId) {
        Map<Object, Object> hash = redis.opsForHash().entries(challengeKey(challengeId));
        return hash.isEmpty() ? Optional.empty() : Optional.of(toChallenge(hash));
    }

    @Override
    public List<Challenge> findPendingByPlayerId(String playerId) {
        return idsOf(CHALLENGE_INDEX_PREFIX, playerId).stream()
                .map(this::findChallengeById)
                .flatMap(Optional::stream)
                .filter(challenge -> challenge.status() == ChallengeStatus.PENDING)
                .toList();
    }

    @Override
    public AcceptOutcome accept(String challengeId, String playerId, ChallengeEvent.ChallengeAcceptedEvent event) {
        Long result = redis.execute(CHALLENGE_ACCEPT,
                List.of(challengeKey(challengeId), OUTBOX_KEY, seqKey(challengeId)),
                playerId, epochMillis(event.acceptedAt()),
                event.getClass().getName(), json(event));
        return switch (result == null ? 0 : result.intValue()) {
            case 1 -> AcceptOutcome.ACCEPTED;
            case 2 -> AcceptOutcome.IDEMPOTENT;
            case 3 -> AcceptOutcome.NOT_OPPONENT;
            default -> AcceptOutcome.NOT_PENDING;
        };
    }

    @Override
    public boolean linkRoom(String challengeId, String roomId, ChallengeEvent.ChallengeRoomCreatedEvent event) {
        Long result = redis.execute(CHALLENGE_LINK,
                List.of(challengeKey(challengeId), OUTBOX_KEY, seqKey(challengeId)),
                roomId, event.getClass().getName(), json(event), challengeId);
        return result != null && result == 1L;
    }

    @Override
    public boolean decline(String challengeId, ChallengeEvent.ChallengeDeclinedEvent event) {
        return terminal(challengeKey(challengeId), "PENDING", "DECLINED", "resolvedAt",
                epochMillis(event.declinedAt()), 0L, "", "", "", "", "", "", "", "",
                event.getClass().getName(), json(event), challengeId);
    }

    @Override
    public boolean cancel(String challengeId, ChallengeEvent.ChallengeCancelledEvent event) {
        return terminal(challengeKey(challengeId), "PENDING", "CANCELLED", "resolvedAt",
                epochMillis(event.cancelledAt()), 0L, "", "", "", "", "", "", "", "",
                event.getClass().getName(), json(event), challengeId);
    }

    @Override
    public boolean expire(String challengeId, ChallengeEvent.ChallengeExpiredEvent event) {
        return terminal(challengeKey(challengeId), "PENDING", "EXPIRED", "resolvedAt",
                epochMillis(event.expiredAt()), 0L, "", "", "", "", "", "", "", "",
                event.getClass().getName(), json(event), challengeId);
    }

    // ============================ internes ============================

    private boolean terminal(String mainKey,
                             String expectedStatus,
                             String newStatus,
                             String dateField,
                             String dateValue,
                             long ttlMs,
                             String extraField1,
                             String extraValue1,
                             String extraField2,
                             String extraValue2,
                             String preconditionField1,
                             String preconditionValue1,
                             String preconditionField2,
                             String preconditionValue2,
                             String eventType,
                             String payload,
                             String aggregateId) {
        Long result = redis.execute(TERMINAL,
                List.of(mainKey, OUTBOX_KEY, seqKey(aggregateId)),
                expectedStatus, newStatus, dateField, dateValue, String.valueOf(ttlMs),
                extraField1, extraValue1, extraField2, extraValue2,
                preconditionField1, preconditionValue1, preconditionField2, preconditionValue2,
                eventType, payload, aggregateId);
        return result != null && result == 1L;
    }

    private void addIndex(String prefix, String playerId, String id) {
        String key = prefix + playerId;
        redis.opsForSet().add(key, id);
        redis.expire(key, INDEX_TTL);
    }

    private List<String> idsOf(String prefix, String playerId) {
        var members = redis.opsForSet().members(prefix + playerId);
        return members == null ? List.of() : List.copyOf(members);
    }

    private Lobby toLobby(Map<Object, Object> hash) {
        return Lobby.builder()
                .lobbyId(str(hash, "lobbyId"))
                .topicId(str(hash, "topicId"))
                .initiatorId(str(hash, "initiatorId"))
                .opponentId(emptyToNull(str(hash, "opponentId")))
                .participantId(emptyToNull(str(hash, "participantId")))
                .gameId(emptyToNull(str(hash, "gameId")))
                .status(LobbyStatus.valueOf(str(hash, "status")))
                .initiatorPresent("1".equals(str(hash, "initiatorPresent")))
                .participantPresent("1".equals(str(hash, "participantPresent")))
                .allPresentAt(instant(hash, "allPresentAt"))
                .readyDeadlineAt(instant(hash, "readyDeadlineAt"))
                .missedReason(emptyToNull(str(hash, "missedReason")))
                .createdAt(instant(hash, "createdAt"))
                .expiresAt(instant(hash, "expiresAt"))
                .updatedAt(instant(hash, "updatedAt"))
                .build();
    }

    private Challenge toChallenge(Map<Object, Object> hash) {
        return Challenge.builder()
                .challengeId(str(hash, "challengeId"))
                .topicId(str(hash, "topicId"))
                .challengerId(str(hash, "challengerId"))
                .opponentId(str(hash, "opponentId"))
                .roomId(emptyToNull(str(hash, "roomId")))
                .status(ChallengeStatus.valueOf(str(hash, "status")))
                .createdAt(instant(hash, "createdAt"))
                .expiresAt(instant(hash, "expiresAt"))
                .resolvedAt(instant(hash, "resolvedAt"))
                .build();
    }

    private static String lobbyKey(String lobbyId) {
        return LOBBY_PREFIX + lobbyId;
    }

    private static String challengeKey(String challengeId) {
        return CHALLENGE_PREFIX + challengeId;
    }

    private static String seqKey(String aggregateId) {
        return SEQ_PREFIX + aggregateId;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String str(Map<Object, Object> hash, String field) {
        Object value = hash.get(field);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Map<Object, Object> hash, String field) {
        String value = str(hash, field);
        return value == null || value.isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(value));
    }

    private static String epochMillis(Instant instant) {
        return String.valueOf(instant.toEpochMilli());
    }

    private String timerMember(String type, String referenceId) {
        return json(new TimerMember(type, referenceId));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Sérialisation JSON impossible", exception);
        }
    }

    private record TimerMember(String type, String referenceId) {
    }

    // ============================ scripts Lua ============================

    private static final String LOBBY_CREATE_SCRIPT = """
            redis.call('HSET', KEYS[1],
                'lobbyId', ARGV[1], 'topicId', ARGV[2], 'initiatorId', ARGV[3], 'opponentId', ARGV[4],
                'status', 'CREATED', 'initiatorPresent', '0', 'participantPresent', '0',
                'expiresAt', ARGV[5], 'createdAt', ARGV[6], 'updatedAt', ARGV[6])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[7]))
            redis.call('ZADD', KEYS[2], ARGV[5], ARGV[8])
            local seq = redis.call('INCR', KEYS[4])
            redis.call('LPUSH', KEYS[3], cjson.encode({
                entryId = ARGV[1] .. ':' .. seq,
                eventType = ARGV[9],
                aggregateId = ARGV[1],
                sequence = seq,
                payload = ARGV[10],
                createdAt = tonumber(ARGV[6])
            }))
            return seq
            """;

    private static final String TERMINAL_LIKE_JOIN_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'status') ~= 'CREATED' then
                return 0
            end
            local participant = redis.call('HGET', KEYS[1], 'participantId')
            if participant and participant ~= '' then
                if participant == ARGV[1] then
                    return 2
                end
                return 3
            end
            local opponent = redis.call('HGET', KEYS[1], 'opponentId')
            if opponent and opponent ~= '' and opponent ~= ARGV[1] then
                return 4
            end
            redis.call('HSET', KEYS[1], 'participantId', ARGV[1], 'updatedAt', ARGV[2])
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = redis.call('HGET', KEYS[1], 'lobbyId') .. ':' .. seq,
                eventType = ARGV[3],
                aggregateId = redis.call('HGET', KEYS[1], 'lobbyId'),
                sequence = seq,
                payload = ARGV[4],
                createdAt = tonumber(ARGV[2])
            }))
            return 1
            """;

    private static final String LOBBY_ENTER_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'status') ~= 'CREATED' then
                return 0
            end
            local initiator = redis.call('HGET', KEYS[1], 'initiatorId')
            local participant = redis.call('HGET', KEYS[1], 'participantId')
            local field
            if ARGV[1] == initiator then
                field = 'initiatorPresent'
            elseif participant and participant ~= '' and ARGV[1] == participant then
                field = 'participantPresent'
            else
                return -1
            end
            if redis.call('HGET', KEYS[1], field) == '1' then
                return 2
            end
            redis.call('HSET', KEYS[1], field, '1', 'updatedAt', ARGV[2])
            local seq = redis.call('INCR', KEYS[4])
            redis.call('LPUSH', KEYS[3], cjson.encode({
                entryId = initiator .. '-enter:' .. seq,
                eventType = ARGV[5],
                aggregateId = redis.call('HGET', KEYS[1], 'lobbyId'),
                sequence = seq,
                payload = ARGV[6],
                createdAt = tonumber(ARGV[2])
            }))
            if redis.call('HGET', KEYS[1], 'initiatorPresent') == '1'
                and redis.call('HGET', KEYS[1], 'participantPresent') == '1' then
                redis.call('HSET', KEYS[1], 'allPresentAt', ARGV[2], 'readyDeadlineAt', ARGV[3])
                redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
                local seq2 = redis.call('INCR', KEYS[4])
                redis.call('LPUSH', KEYS[3], cjson.encode({
                    entryId = initiator .. '-ready:' .. seq2,
                    eventType = ARGV[7],
                    aggregateId = redis.call('HGET', KEYS[1], 'lobbyId'),
                    sequence = seq2,
                    payload = ARGV[8],
                    createdAt = tonumber(ARGV[2])
                }))
                return 3
            end
            return 1
            """;

    private static final String LOBBY_LEAVE_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'status') ~= 'CREATED' then
                return 0
            end
            local initiator = redis.call('HGET', KEYS[1], 'initiatorId')
            local participant = redis.call('HGET', KEYS[1], 'participantId')
            local field
            if ARGV[1] == initiator then
                field = 'initiatorPresent'
            elseif participant and participant ~= '' and ARGV[1] == participant then
                field = 'participantPresent'
            else
                return -1
            end
            if redis.call('HGET', KEYS[1], field) ~= '1' then
                return 2
            end
            redis.call('HSET', KEYS[1], field, '0', 'updatedAt', ARGV[2])
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = initiator .. '-leave:' .. seq,
                eventType = ARGV[3],
                aggregateId = redis.call('HGET', KEYS[1], 'lobbyId'),
                sequence = seq,
                payload = ARGV[4],
                createdAt = tonumber(ARGV[2])
            }))
            return 1
            """;

    private static final String CHALLENGE_CREATE_SCRIPT = """
            redis.call('HSET', KEYS[1],
                'challengeId', ARGV[1], 'topicId', ARGV[2], 'challengerId', ARGV[3],
                'opponentId', ARGV[4], 'status', 'PENDING',
                'expiresAt', ARGV[5], 'createdAt', ARGV[6])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[7]))
            redis.call('ZADD', KEYS[2], ARGV[5], ARGV[8])
            local seq = redis.call('INCR', KEYS[4])
            redis.call('LPUSH', KEYS[3], cjson.encode({
                entryId = ARGV[1] .. ':' .. seq,
                eventType = ARGV[9],
                aggregateId = ARGV[1],
                sequence = seq,
                payload = ARGV[10],
                createdAt = tonumber(ARGV[6])
            }))
            return seq
            """;

    private static final String CHALLENGE_ACCEPT_SCRIPT = """
            local status = redis.call('HGET', KEYS[1], 'status')
            if status == 'ACCEPTED' then
                return 2
            end
            if status ~= 'PENDING' then
                return 0
            end
            if redis.call('HGET', KEYS[1], 'opponentId') ~= ARGV[1] then
                return 3
            end
            redis.call('HSET', KEYS[1], 'status', 'ACCEPTED', 'resolvedAt', ARGV[2])
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = redis.call('HGET', KEYS[1], 'challengeId') .. ':' .. seq,
                eventType = ARGV[3],
                aggregateId = redis.call('HGET', KEYS[1], 'challengeId'),
                sequence = seq,
                payload = ARGV[4],
                createdAt = tonumber(ARGV[2])
            }))
            return 1
            """;

    private static final String CHALLENGE_LINK_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'roomId') and redis.call('HGET', KEYS[1], 'roomId') ~= '' then
                return 0
            end
            redis.call('HSET', KEYS[1], 'roomId', ARGV[1])
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = ARGV[4] .. ':' .. seq,
                eventType = ARGV[2],
                aggregateId = ARGV[4],
                sequence = seq,
                payload = ARGV[3],
                createdAt = redis.call('TIME')[1] * 1000
            }))
            return 1
            """;

    private static final String TERMINAL_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'status') ~= ARGV[1] then
                return 0
            end
            if ARGV[10] ~= '' and redis.call('HGET', KEYS[1], ARGV[10]) ~= ARGV[11] then
                return 0
            end
            if ARGV[12] ~= '' and redis.call('HGET', KEYS[1], ARGV[12]) ~= ARGV[13] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'status', ARGV[2], ARGV[3], ARGV[4])
            if ARGV[6] ~= '' then
                redis.call('HSET', KEYS[1], ARGV[6], ARGV[7])
            end
            if ARGV[8] ~= '' then
                redis.call('HSET', KEYS[1], ARGV[8], ARGV[9])
            end
            local ttl = tonumber(ARGV[5])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
            end
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = ARGV[16] .. ':' .. seq,
                eventType = ARGV[14],
                aggregateId = ARGV[16],
                sequence = seq,
                payload = ARGV[15],
                createdAt = tonumber(ARGV[4])
            }))
            return 1
            """;

    private static final RedisScript<Long> LOBBY_CREATE = new DefaultRedisScript<>(LOBBY_CREATE_SCRIPT, Long.class);
    private static final RedisScript<Long> JOIN = new DefaultRedisScript<>(TERMINAL_LIKE_JOIN_SCRIPT, Long.class);
    private static final RedisScript<Long> ENTER = new DefaultRedisScript<>(LOBBY_ENTER_SCRIPT, Long.class);
    private static final RedisScript<Long> LEAVE = new DefaultRedisScript<>(LOBBY_LEAVE_SCRIPT, Long.class);
    private static final RedisScript<Long> CHALLENGE_CREATE = new DefaultRedisScript<>(CHALLENGE_CREATE_SCRIPT, Long.class);
    private static final RedisScript<Long> CHALLENGE_ACCEPT = new DefaultRedisScript<>(CHALLENGE_ACCEPT_SCRIPT, Long.class);
    private static final RedisScript<Long> CHALLENGE_LINK = new DefaultRedisScript<>(CHALLENGE_LINK_SCRIPT, Long.class);
    private static final RedisScript<Long> TERMINAL = new DefaultRedisScript<>(TERMINAL_SCRIPT, Long.class);
}
