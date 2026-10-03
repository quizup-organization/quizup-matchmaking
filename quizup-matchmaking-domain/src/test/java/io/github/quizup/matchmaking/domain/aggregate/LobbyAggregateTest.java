package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
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
    private static final String OPPONENT = "opponent-1";
    private static final String PARTICIPANT = "player-2";

    private final AggregateTestFixture<LobbyAggregate> fixture =
            new AggregateTestFixture<>(LobbyAggregate.class);

    LobbyAggregateTest() {
        fixture.registerInjectableResource((ProfileRepositoryPort) id -> null);
        fixture.registerInjectableResource((TopicAvailabilityPort) (topicId, languages) -> true);
    }

    @Test
    void createLobby_appliesCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(new LobbyCommand.CreateLobbyCommand(LOBBY_ID, TOPIC, INITIATOR, null))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        LobbyEvent.LobbyCreatedEvent.class,
                        e -> ((LobbyEvent.LobbyCreatedEvent) e).lobbyId().equals(LOBBY_ID)
                                && ((LobbyEvent.LobbyCreatedEvent) e).initiatorId().equals(INITIATOR)
                                && ((LobbyEvent.LobbyCreatedEvent) e).opponentId() == null));
    }

    @Test
    void createNominalChallenge_appliesCreatedEventWithOpponent() {
        fixture.givenNoPriorActivity()
                .when(new LobbyCommand.CreateLobbyCommand(LOBBY_ID, TOPIC, INITIATOR, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        LobbyEvent.LobbyCreatedEvent.class,
                        e -> OPPONENT.equals(((LobbyEvent.LobbyCreatedEvent) e).opponentId())));
    }

    @Test
    void cannotChallengeSelf() {
        fixture.givenNoPriorActivity()
                .when(new LobbyCommand.CreateLobbyCommand(LOBBY_ID, TOPIC, INITIATOR, INITIATOR))
                .expectException(LobbyExceptions.CannotChallengeSelfProblem.class);
    }

    @Test
    void joiningTwiceIsIdempotent() {
        fixture.given(created(), joined())
                .when(new LobbyCommand.JoinLobbyCommand(LOBBY_ID, PARTICIPANT))
                .expectNoEvents();
    }

    @Test
    void nominalChallenge_refusesOtherPlayer() {
        fixture.given(createdWithOpponent())
                .when(new LobbyCommand.JoinLobbyCommand(LOBBY_ID, PARTICIPANT))
                .expectException(LobbyExceptions.LobbyNotInvitedProblem.class);
    }

    @Test
    void nominalChallenge_letsInvitedPlayerJoin() {
        fixture.given(createdWithOpponent())
                .when(new LobbyCommand.JoinLobbyCommand(LOBBY_ID, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        LobbyEvent.LobbyJoinedEvent.class,
                        e -> OPPONENT.equals(((LobbyEvent.LobbyJoinedEvent) e).participantId())));
    }

    @Test
    void nominalChallenge_declinedByInvitedPlayer() {
        fixture.given(createdWithOpponent())
                .when(new LobbyCommand.DeclineLobbyCommand(LOBBY_ID, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        LobbyEvent.LobbyDeclinedEvent.class,
                        e -> OPPONENT.equals(((LobbyEvent.LobbyDeclinedEvent) e).opponentId())));
    }

    @Test
    void nominalChallenge_declineRefusedToOthers() {
        fixture.given(createdWithOpponent())
                .when(new LobbyCommand.DeclineLobbyCommand(LOBBY_ID, PARTICIPANT))
                .expectException(LobbyExceptions.LobbyNotInvitedProblem.class);
    }

    @Test
    void complete_closesLobbyWithoutImmediatePurge() {
        fixture.given(created(), joined())
                .when(new LobbyCommand.CompleteLobbyCommand(LOBBY_ID, "game-1"))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
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
                LOBBY_ID, TOPIC, INITIATOR, null, Instant.now().plusSeconds(3600), Instant.now());
    }

    private static LobbyEvent.LobbyCreatedEvent createdWithOpponent() {
        return new LobbyEvent.LobbyCreatedEvent(
                LOBBY_ID, TOPIC, INITIATOR, OPPONENT, Instant.now().plusSeconds(3600), Instant.now());
    }

    private static LobbyEvent.LobbyJoinedEvent joined() {
        return new LobbyEvent.LobbyJoinedEvent(LOBBY_ID, PARTICIPANT, Instant.now());
    }
}
