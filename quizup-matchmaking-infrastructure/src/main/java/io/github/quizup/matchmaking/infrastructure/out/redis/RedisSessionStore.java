package io.github.quizup.matchmaking.infrastructure.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import io.github.quizup.matchmaking.domain.model.SessionTimer;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingStorePort;
import io.github.quizup.matchmaking.domain.port.out.SessionOutboxPort;
import io.github.quizup.matchmaking.domain.port.out.SessionTimerPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Store chaud Redis du session tier d'appariement : tickets, file par sujet, timers et outbox.
 *
 * <p>Chaque transition critique est un script Lua atomique (claim d'une paire, bascule bot,
 * annulation) : l'état et l'événement d'outbox sont écrits ensemble. Le relais publie ensuite
 * les événements sur le bus Axon (event store + Kafka), ce qui préserve l'historique et les
 * notifications du BFF sans event sourcing des agrégats éphémères.</p>
 *
 * <p>Clés : {@code session:mm:ticket:{id}} (TTL 10 min), {@code session:mm:queue:{topic}},
 * {@code session:timers} (ZSET), {@code session:outbox} (liste + {@code :inflight}),
 * {@code session:seq:{ticketId}} (séquence d'événements).</p>
 */
@Component
public class RedisSessionStore implements MatchmakingStorePort, SessionTimerPort, SessionOutboxPort {

    private static final String TICKET_PREFIX = "session:mm:ticket:";
    private static final String QUEUE_PREFIX = "session:mm:queue:";
    private static final String TIMERS_KEY = "session:timers";
    private static final String OUTBOX_KEY = "session:outbox";
    private static final String INFLIGHT_KEY = "session:outbox:inflight";
    private static final String SEQ_PREFIX = "session:seq:";
    private static final long TICKET_TTL_MS = Duration.ofMinutes(10).toMillis();

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ============================ MatchmakingStorePort ============================

    @Override
    public void enqueue(Matchmaking ticket, MatchmakingEvent.MatchmakingStartedEvent event) {
        redis.execute(ENQUEUE_SCRIPT,
                List.of(ticketKey(ticket.matchmakingId()), queueKey(ticket.topicId()),
                        TIMERS_KEY, OUTBOX_KEY, seqKey(ticket.matchmakingId())),
                ticket.matchmakingId(),
                ticket.playerId(),
                ticket.topicId(),
                String.valueOf(ticket.level()),
                encodeLanguages(ticket.languages()),
                epochMillis(ticket.createdAt()),
                epochMillis(ticket.updatedAt()),
                epochMillis(ticket.createdAt().plus(LobbyDeadline.MATCHMAKING_DEADLINE_DURATION)),
                timerMember(SessionTimer.MATCHMAKING_DEADLINE, ticket.matchmakingId()),
                event.getClass().getName(),
                json(event),
                String.valueOf(TICKET_TTL_MS));
    }

    @Override
    public Optional<Matchmaking> findById(String ticketId) {
        Map<Object, Object> hash = redis.opsForHash().entries(ticketKey(ticketId));
        return hash.isEmpty() ? Optional.empty() : Optional.of(toTicket(hash));
    }

    @Override
    public List<Matchmaking> candidates(String topicId, Instant olderThan, int limit) {
        Set<String> ids = redis.opsForZSet()
                .rangeByScore(queueKey(topicId), 0, olderThan.toEpochMilli() - 1, 0, limit);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(id -> redis.opsForHash().entries(ticketKey(id)))
                .filter(hash -> !hash.isEmpty())
                .map(this::toTicket)
                .toList();
    }

    @Override
    public boolean claimPair(String firstTicketId, String secondTicketId) {
        Long claimed = redis.execute(CLAIM_PAIR_SCRIPT,
                List.of(ticketKey(firstTicketId), ticketKey(secondTicketId),
                        queueKeyOf(firstTicketId), queueKeyOf(secondTicketId)),
                firstTicketId, secondTicketId);
        return claimed != null && claimed == 1L;
    }

    @Override
    public void completePair(String firstTicketId,
                             String secondTicketId,
                             MatchmakingEvent.MatchmakingMatchedEvent firstEvent,
                             MatchmakingEvent.MatchmakingMatchedEvent secondEvent) {
        redis.execute(COMPLETE_PAIR_SCRIPT,
                List.of(ticketKey(firstTicketId), ticketKey(secondTicketId), OUTBOX_KEY,
                        seqKey(firstTicketId), seqKey(secondTicketId)),
                firstTicketId, secondTicketId,
                firstEvent.opponentId(), secondEvent.opponentId(),
                firstEvent.gameId(), epochMillis(firstEvent.matchedAt()),
                firstEvent.getClass().getName(), json(firstEvent), json(secondEvent));
    }

    @Override
    public boolean claimForBot(String ticketId) {
        Long claimed = redis.execute(CLAIM_BOT_SCRIPT,
                List.of(ticketKey(ticketId), queueKeyOf(ticketId)), ticketId);
        return claimed != null && claimed == 1L;
    }

    @Override
    public void completeBot(String ticketId, MatchmakingEvent.MatchmakingMatchedEvent event) {
        redis.execute(COMPLETE_BOT_SCRIPT,
                List.of(ticketKey(ticketId), OUTBOX_KEY, seqKey(ticketId)),
                ticketId, event.gameId(), epochMillis(event.matchedAt()),
                event.getClass().getName(), json(event));
    }

    @Override
    public void fail(String ticketId, MatchmakingEvent.MatchmakingFailedEvent event) {
        redis.execute(FAIL_SCRIPT,
                List.of(ticketKey(ticketId), OUTBOX_KEY, seqKey(ticketId)),
                ticketId, event.reason(), epochMillis(event.failedAt()),
                event.getClass().getName(), json(event));
    }

    @Override
    public boolean cancel(String ticketId, MatchmakingEvent.MatchmakingCancelledEvent event) {
        Long cancelled = redis.execute(CANCEL_SCRIPT,
                List.of(ticketKey(ticketId), queueKeyOf(ticketId), OUTBOX_KEY, seqKey(ticketId)),
                ticketId, event.reason(), epochMillis(event.cancelledAt()),
                event.getClass().getName(), json(event));
        return cancelled != null && cancelled == 1L;
    }

    // ============================ SessionTimerPort ============================

    @Override
    public void schedule(String type, String referenceId, Instant fireAt) {
        redis.opsForZSet().add(TIMERS_KEY, timerMember(type, referenceId), fireAt.toEpochMilli());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SessionTimer.Due> claimDue(Instant now, int limit) {
        List<String> members = (List<String>) redis.execute(TIMER_CLAIM_SCRIPT,
                List.of(TIMERS_KEY), epochMillis(now), String.valueOf(limit));
        if (members == null) {
            return List.of();
        }
        return members.stream().map(this::parseTimer).toList();
    }

    // ============================ SessionOutboxPort ============================

    @Override
    public Optional<OutboxEntry> next() {
        String raw = redis.opsForList().rightPopAndLeftPush(OUTBOX_KEY, INFLIGHT_KEY);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = objectMapper.readValue(raw, Map.class);
            return Optional.of(new OutboxEntry(
                    (String) fields.get("entryId"),
                    (String) fields.get("eventType"),
                    (String) fields.get("aggregateId"),
                    ((Number) fields.get("sequence")).longValue(),
                    (String) fields.get("payload"),
                    Instant.ofEpochMilli(((Number) fields.get("createdAt")).longValue()),
                    raw));
        } catch (Exception exception) {
            throw new IllegalStateException("Entrée d'outbox illisible: " + raw, exception);
        }
    }

    @Override
    public void acknowledge(OutboxEntry entry) {
        redis.opsForList().remove(INFLIGHT_KEY, 1, entry.raw());
    }

    @Override
    public void recoverInFlight() {
        while (redis.opsForList().rightPopAndLeftPush(INFLIGHT_KEY, OUTBOX_KEY) != null) {
            // draine la file « en vol » vers l'outbox
        }
    }

    // ============================ internes ============================

    private Matchmaking toTicket(Map<Object, Object> hash) {
        return Matchmaking.builder()
                .matchmakingId(str(hash, "ticketId"))
                .playerId(str(hash, "playerId"))
                .topicId(str(hash, "topicId"))
                .level(Integer.parseInt(str(hash, "level")))
                .languages(decodeLanguages(str(hash, "languages")))
                .opponentId(str(hash, "opponentId"))
                .gameId(str(hash, "gameId"))
                .vsBot("1".equals(str(hash, "vsBot")))
                .status(MatchmakingStatus.valueOf(str(hash, "status")))
                .createdAt(instant(hash, "createdAt"))
                .updatedAt(instant(hash, "updatedAt"))
                .build();
    }

    private static String ticketKey(String ticketId) {
        return TICKET_PREFIX + ticketId;
    }

    private static String queueKey(String topicId) {
        return QUEUE_PREFIX + topicId;
    }

    private String queueKeyOf(String ticketId) {
        // la file est déduite du ticket (même script : lecture du hash avant ZREM)
        String topicId = (String) redis.opsForHash().get(ticketKey(ticketId), "topicId");
        return queueKey(topicId == null ? "" : topicId);
    }

    private static String seqKey(String ticketId) {
        return SEQ_PREFIX + ticketId;
    }

    private static String encodeLanguages(Set<Language> languages) {
        if (languages == null || languages.isEmpty()) {
            return "";
        }
        return languages.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static Set<Language> decodeLanguages(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(encoded.split(","))
                .filter(code -> !code.isBlank())
                .map(Language::valueOf)
                .collect(Collectors.toSet());
    }

    private static String str(Map<Object, Object> hash, String field) {
        Object value = hash.get(field);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Map<Object, Object> hash, String field) {
        String value = str(hash, field);
        return value == null ? null : Instant.ofEpochMilli(Long.parseLong(value));
    }

    private static String epochMillis(Instant instant) {
        return String.valueOf(instant.toEpochMilli());
    }

    private String timerMember(String type, String referenceId) {
        return json(new TimerMember(type, referenceId));
    }

    private SessionTimer.Due parseTimer(String member) {
        try {
            TimerMember parsed = objectMapper.readValue(member, TimerMember.class);
            return new SessionTimer.Due(parsed.type(), parsed.referenceId());
        } catch (Exception exception) {
            throw new IllegalStateException("Timer illisible: " + member, exception);
        }
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

    private static final String ENQUEUE_LUA = """
            local ticketKey = KEYS[1]
            local queueKey = KEYS[2]
            local timersKey = KEYS[3]
            local outboxKey = KEYS[4]
            local seqKey = KEYS[5]

            redis.call('HSET', ticketKey,
                'ticketId', ARGV[1], 'playerId', ARGV[2], 'topicId', ARGV[3], 'level', ARGV[4],
                'languages', ARGV[5], 'status', 'SEARCHING', 'vsBot', '0',
                'createdAt', ARGV[6], 'updatedAt', ARGV[7])
            redis.call('PEXPIRE', ticketKey, tonumber(ARGV[12]))
            redis.call('ZADD', queueKey, ARGV[6], ARGV[1])
            redis.call('ZADD', timersKey, ARGV[8], ARGV[9])
            local seq = redis.call('INCR', seqKey)
            local entry = cjson.encode({
                entryId = ARGV[1] .. ':' .. seq,
                eventType = ARGV[10],
                aggregateId = ARGV[1],
                sequence = seq,
                payload = ARGV[11],
                createdAt = tonumber(ARGV[7])
            })
            redis.call('LPUSH', outboxKey, entry)
            return seq
            """;

    private static final String CLAIM_PAIR_LUA = """
            local first = redis.call('HGET', KEYS[1], 'status')
            local second = redis.call('HGET', KEYS[2], 'status')
            if first ~= 'SEARCHING' or second ~= 'SEARCHING' then
                return 0
            end
            redis.call('ZREM', KEYS[3], ARGV[1])
            redis.call('ZREM', KEYS[4], ARGV[2])
            redis.call('HSET', KEYS[1], 'status', 'CLOSED')
            redis.call('HSET', KEYS[2], 'status', 'CLOSED')
            return 1
            """;

    private static final String COMPLETE_PAIR_LUA = """
            redis.call('HSET', KEYS[1], 'status', 'CLOSED', 'opponentId', ARGV[3],
                'gameId', ARGV[5], 'vsBot', '0', 'updatedAt', ARGV[6])
            redis.call('HSET', KEYS[2], 'status', 'CLOSED', 'opponentId', ARGV[4],
                'gameId', ARGV[5], 'vsBot', '0', 'updatedAt', ARGV[6])
            local firstSeq = redis.call('INCR', KEYS[4])
            redis.call('LPUSH', KEYS[3], cjson.encode({
                entryId = ARGV[1] .. ':' .. firstSeq,
                eventType = ARGV[7],
                aggregateId = ARGV[1],
                sequence = firstSeq,
                payload = ARGV[8],
                createdAt = tonumber(ARGV[6])
            }))
            local secondSeq = redis.call('INCR', KEYS[5])
            redis.call('LPUSH', KEYS[3], cjson.encode({
                entryId = ARGV[2] .. ':' .. secondSeq,
                eventType = ARGV[7],
                aggregateId = ARGV[2],
                sequence = secondSeq,
                payload = ARGV[9],
                createdAt = tonumber(ARGV[6])
            }))
            return firstSeq + secondSeq
            """;

    private static final String CLAIM_BOT_LUA = """
            if redis.call('HGET', KEYS[1], 'status') ~= 'SEARCHING' then
                return 0
            end
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('HSET', KEYS[1], 'status', 'CLOSED')
            return 1
            """;

    private static final String COMPLETE_BOT_LUA = """
            redis.call('HSET', KEYS[1], 'status', 'CLOSED', 'gameId', ARGV[2],
                'vsBot', '1', 'updatedAt', ARGV[3])
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = ARGV[1] .. ':' .. seq,
                eventType = ARGV[4],
                aggregateId = ARGV[1],
                sequence = seq,
                payload = ARGV[5],
                createdAt = tonumber(ARGV[3])
            }))
            return seq
            """;

    private static final String FAIL_LUA = """
            if redis.call('HGET', KEYS[1], 'status') == 'FAILED' then
                return 0
            end
            redis.call('HSET', KEYS[1], 'status', 'FAILED', 'failureReason', ARGV[2],
                'updatedAt', ARGV[3])
            local seq = redis.call('INCR', KEYS[3])
            redis.call('LPUSH', KEYS[2], cjson.encode({
                entryId = ARGV[1] .. ':' .. seq,
                eventType = ARGV[4],
                aggregateId = ARGV[1],
                sequence = seq,
                payload = ARGV[5],
                createdAt = tonumber(ARGV[3])
            }))
            return 1
            """;

    private static final String CANCEL_LUA = """
            if redis.call('HGET', KEYS[1], 'status') ~= 'SEARCHING' then
                return 0
            end
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('HSET', KEYS[1], 'status', 'CLOSED', 'cancelReason', ARGV[2],
                'updatedAt', ARGV[3])
            local seq = redis.call('INCR', KEYS[4])
            redis.call('LPUSH', KEYS[3], cjson.encode({
                entryId = ARGV[1] .. ':' .. seq,
                eventType = ARGV[4],
                aggregateId = ARGV[1],
                sequence = seq,
                payload = ARGV[5],
                createdAt = tonumber(ARGV[3])
            }))
            return 1
            """;

    private static final String TIMER_CLAIM_LUA = """
            local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))
            if #due == 0 then
                return due
            end
            for i, member in ipairs(due) do
                redis.call('ZREM', KEYS[1], member)
            end
            return due
            """;

    private static final RedisScript<Long> ENQUEUE_SCRIPT =
            new DefaultRedisScript<>(ENQUEUE_LUA, Long.class);
    private static final RedisScript<Long> CLAIM_PAIR_SCRIPT =
            new DefaultRedisScript<>(CLAIM_PAIR_LUA, Long.class);
    private static final RedisScript<Long> COMPLETE_PAIR_SCRIPT =
            new DefaultRedisScript<>(COMPLETE_PAIR_LUA, Long.class);
    private static final RedisScript<Long> CLAIM_BOT_SCRIPT =
            new DefaultRedisScript<>(CLAIM_BOT_LUA, Long.class);
    private static final RedisScript<Long> COMPLETE_BOT_SCRIPT =
            new DefaultRedisScript<>(COMPLETE_BOT_LUA, Long.class);
    private static final RedisScript<Long> FAIL_SCRIPT =
            new DefaultRedisScript<>(FAIL_LUA, Long.class);
    private static final RedisScript<Long> CANCEL_SCRIPT =
            new DefaultRedisScript<>(CANCEL_LUA, Long.class);
    private static final RedisScript<List> TIMER_CLAIM_SCRIPT =
            new DefaultRedisScript<>(TIMER_CLAIM_LUA, List.class);
}
