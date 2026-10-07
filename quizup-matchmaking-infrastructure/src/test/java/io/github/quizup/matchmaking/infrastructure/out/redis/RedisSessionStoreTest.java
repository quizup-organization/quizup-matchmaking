package io.github.quizup.matchmaking.infrastructure.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import io.github.quizup.matchmaking.domain.model.SessionTimer;
import io.github.quizup.matchmaking.domain.port.out.SessionOutboxPort.OutboxEntry;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration des scripts Lua du session tier (Testcontainers Redis) : file d'attente,
 * claim atomique de paire, bascule bot, annulation, timers et outbox.
 */
class RedisSessionStoreTest {

    private static final String TOPIC = "topic-1";
    private static final String MATCHED_EVENT_TYPE =
            "io.github.quizup.matchmaking.domain.event.MatchmakingEvent$MatchmakingMatchedEvent";
    private static final String FAILED_EVENT_TYPE =
            "io.github.quizup.matchmaking.domain.event.MatchmakingEvent$MatchmakingFailedEvent";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;

    private StringRedisTemplate redis;
    private RedisSessionStore store;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = new LettuceConnectionFactory(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        redis = new StringRedisTemplate(connectionFactory);
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        store = new RedisSessionStore(redis, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void enqueue_thenFindAndCandidates() {
        Instant older = Instant.now().minusMillis(10_000);
        enqueue("t1", "u1", 10, older);
        enqueue("t2", "u2", 10, Instant.now());

        assertThat(store.findById("t1")).isPresent()
                .get().extracting(Matchmaking::status).isEqualTo(MatchmakingStatus.SEARCHING);
        assertThat(store.candidates(TOPIC, Instant.now(), 10))
                .extracting(Matchmaking::matchmakingId)
                .containsExactly("t1", "t2");
        assertThat(store.candidates(TOPIC, older.plusMillis(1), 10))
                .extracting(Matchmaking::matchmakingId)
                .containsExactly("t1");
    }

    @Test
    void claimPair_isAtomic_thenNoLongerSearching() {
        enqueue("t1", "u1", 10, Instant.now().minusSeconds(1));
        enqueue("t2", "u2", 10, Instant.now());

        assertThat(store.claimPair("t1", "t2")).isTrue();
        assertThat(store.claimPair("t1", "t2")).isFalse();
        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.CLOSED);
        assertThat(store.candidates(TOPIC, Instant.now(), 10)).isEmpty();
    }

    @Test
    void cancel_onlyWhileSearching() {
        enqueue("t1", "u1", 10, Instant.now());

        MatchmakingEvent.MatchmakingCancelledEvent event =
                new MatchmakingEvent.MatchmakingCancelledEvent("t1", "PLAYER_CANCELLED", Instant.now());
        assertThat(store.cancel("t1", event)).isTrue();
        assertThat(store.cancel("t1", event)).isFalse();
        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.CLOSED);
        assertThat(store.candidates(TOPIC, Instant.now(), 10)).isEmpty();
    }

    @Test
    void completePair_setsGameOnBothTickets_andWritesOutbox() {
        enqueue("t1", "u1", 10, Instant.now().minusSeconds(1));
        enqueue("t2", "u2", 10, Instant.now());
        store.claimPair("t1", "t2");
        while (store.next().isPresent()) {
            // draine les événements Started de l'enfilement
        }

        Instant now = Instant.now();
        store.completePair("t1", "t2",
                new MatchmakingEvent.MatchmakingMatchedEvent("t1", "u2", "game-1", false, now),
                new MatchmakingEvent.MatchmakingMatchedEvent("t2", "u1", "game-1", false, now));

        assertThat(store.findById("t1")).get().satisfies(ticket -> {
            assertThat(ticket.gameId()).isEqualTo("game-1");
            assertThat(ticket.opponentId()).isEqualTo("u2");
        });
        OutboxEntry first = store.next().orElseThrow();
        OutboxEntry second = store.next().orElseThrow();
        assertThat(List.of(first.sequence(), second.sequence())).containsExactlyInAnyOrder(2L, 2L);
        assertThat(first.eventType()).isEqualTo(MATCHED_EVENT_TYPE);
        store.acknowledge(first);
        store.acknowledge(second);
        assertThat(store.next()).isEmpty();
    }

    @Test
    void botFallback_claimsTicketOnceAndCompletesGame() {
        enqueue("t1", "u1", 10, Instant.now());

        assertThat(store.claimForBot("t1")).isTrue();
        assertThat(store.claimForBot("t1")).isFalse();

        store.completeBot("t1", new MatchmakingEvent.MatchmakingMatchedEvent(
                "t1", null, "game-bot", true, Instant.now()));
        assertThat(store.findById("t1")).get().satisfies(ticket -> {
            assertThat(ticket.vsBot()).isTrue();
            assertThat(ticket.gameId()).isEqualTo("game-bot");
        });
        assertThat(store.next()).isPresent();
    }

    @Test
    void fail_marksTicketFailedAndWritesOutboxOnce() {
        enqueue("t1", "u1", 10, Instant.now());

        store.fail("t1", new MatchmakingEvent.MatchmakingFailedEvent(
                "t1", "GAME_CREATION_FAILED", Instant.now()));
        store.fail("t1", new MatchmakingEvent.MatchmakingFailedEvent(
                "t1", "GAME_CREATION_FAILED", Instant.now()));

        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.FAILED);
        List<String> eventTypes = new java.util.ArrayList<>();
        Optional<OutboxEntry> next;
        while ((next = store.next()).isPresent()) {
            eventTypes.add(next.get().eventType());
        }
        assertThat(eventTypes).contains(FAILED_EVENT_TYPE);
        assertThat(eventTypes).hasSize(2);
    }

    @Test
    void timers_areClaimedAtomicallyOnceThenGone() {
        Instant now = Instant.now();
        store.schedule(SessionTimer.MATCHMAKING_DEADLINE, "t1", now.minusSeconds(1));
        store.schedule(SessionTimer.LOBBY_EXPIRY, "l1", now.plusSeconds(60));

        List<SessionTimer.Due> due = store.claimDue(now, 10);

        assertThat(due).containsExactly(new SessionTimer.Due(SessionTimer.MATCHMAKING_DEADLINE, "t1"));
        assertThat(store.claimDue(now, 10)).isEmpty();
    }

    @Test
    void outbox_inFlightEntriesAreRecovered() {
        enqueue("t1", "u1", 10, Instant.now());

        Optional<OutboxEntry> claimed = store.next();
        assertThat(claimed).isPresent();
        assertThat(store.next()).isEmpty();

        store.recoverInFlight();

        assertThat(store.next()).isPresent();
    }

    private void enqueue(String ticketId, String playerId, int level, Instant createdAt) {
        Matchmaking ticket = Matchmaking.builder()
                .matchmakingId(ticketId)
                .playerId(playerId)
                .topicId(TOPIC)
                .level(level)
                .languages(Set.of(Language.FR))
                .vsBot(false)
                .status(MatchmakingStatus.SEARCHING)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        store.enqueue(ticket, new MatchmakingEvent.MatchmakingStartedEvent(
                ticketId, playerId, TOPIC, level, Set.of(Language.FR), createdAt));
    }
}
