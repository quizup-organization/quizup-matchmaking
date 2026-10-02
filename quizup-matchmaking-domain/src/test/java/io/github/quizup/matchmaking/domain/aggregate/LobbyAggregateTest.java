package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * Test Axon in-memory de {@link LobbyAggregate} (salon privé, deux humains).
 */
class LobbyAggregateTest {

    private static final String LOBBY_ID = "lobby-1";
    private static final String TOPIC = "topic-1";
    private static final String INITIATOR = "initiator-1";
    private static final String PARTICIPANT = "player-2";

    private final AggregateTestFixture<LobbyAggregate> fixture =
            new AggregateTestFixture<>(LobbyAggregate.class);

    @Test
    void createLobby_appliesCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(new LobbyCommand.CreateLobbyCommand(LOBBY_ID, TOPIC, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        LobbyEvent.LobbyCreatedEvent.class,
                        e -> ((LobbyEvent.LobbyCreatedEvent) e).lobbyId().equals(LOBBY_ID)
                                && ((LobbyEvent.LobbyCreatedEvent) e).initiatorId().equals(INITIATOR)));
    }

    @Test
    void joiningTwiceIsIdempotent() {
        fixture.given(created(), joined())
                .when(new LobbyCommand.JoinLobbyCommand(LOBBY_ID, PARTICIPANT))
                .expectNoEvents();
    }

    @Test
    void complete_purgesLobby() {
        fixture.given(created(), joined())
                .when(new LobbyCommand.CompleteLobbyCommand(LOBBY_ID, "game-1"))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        LobbyEvent.LobbyCompletedEvent.class,
                        e -> "game-1".equals(((LobbyEvent.LobbyCompletedEvent) e).gameId())));
    }

    @Test
    void leave_cancelsLobby() {
        fixture.given(created(), joined())
                .when(new LobbyCommand.LeaveLobbyCommand(LOBBY_ID, PARTICIPANT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        LobbyEvent.LobbyCancelledEvent.class,
                        e -> "PLAYER_LEFT".equals(((LobbyEvent.LobbyCancelledEvent) e).reason())));
    }

    @Test
    void fail_setsFailed() {
        fixture.given(created(), joined())
                .when(new LobbyCommand.FailLobbyCommand(LOBBY_ID, "GAME_CREATION_FAILED"))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        LobbyEvent.LobbyFailedEvent.class,
                        e -> "GAME_CREATION_FAILED".equals(((LobbyEvent.LobbyFailedEvent) e).reason())));
    }

    @Test
    void purgeLobby_leavesAggregateDeleted_soItCannotBeReloaded() {
        fixture.given(created(), new LobbyEvent.LobbyPurgedEvent(LOBBY_ID, Instant.now()))
                .when(new LobbyCommand.JoinLobbyCommand(LOBBY_ID, PARTICIPANT))
                .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
    }

    private static LobbyEvent.LobbyCreatedEvent created() {
        return new LobbyEvent.LobbyCreatedEvent(
                LOBBY_ID, TOPIC, INITIATOR, Instant.now().plusSeconds(3600), Instant.now());
    }

    private static LobbyEvent.LobbyJoinedEvent joined() {
        return new LobbyEvent.LobbyJoinedEvent(LOBBY_ID, PARTICIPANT, Instant.now());
    }
}
