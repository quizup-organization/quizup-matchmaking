package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

/**
 * Test Axon in-memory de l'agrégat {@link LobbyAggregate} via {@link AggregateTestFixture}.
 * <p>
 * 100 % in-memory : event store de l'agrégat en mémoire, aucun Postgres ni Axon Server.
 */
class LobbyAggregateTest {

    private final AggregateTestFixture<LobbyAggregate> fixture =
            new AggregateTestFixture<>(LobbyAggregate.class);

    @Test
    void openLobby_appliesLobbyOpenedEvent() {
        LobbyCommand.OpenLobbyCommand command = new LobbyCommand.OpenLobbyCommand(
                "lobby-1", "initiator-1", "topic-1");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        LobbyEvent.LobbyOpenedEvent.class,
                        e -> ((LobbyEvent.LobbyOpenedEvent) e).lobbyId().equals("lobby-1")
                                && ((LobbyEvent.LobbyOpenedEvent) e).initiatorId().equals("initiator-1")));
    }

    @Test
    void purgeLobby_afterCompletion_appliesPurgedEvent_andDeletesAggregate() {
        LobbyEvent.LobbyOpenedEvent opened =
                new LobbyEvent.LobbyOpenedEvent("lobby-1", "topic-1", "initiator-1", java.time.Instant.now());
        LobbyEvent.LobbyJoinedEvent joined =
                new LobbyEvent.LobbyJoinedEvent("lobby-1", "challenger-1", LobbyParticipantType.HUMAN, java.time.Instant.now());
        LobbyEvent.LobbyCompletedEvent completed =
                new LobbyEvent.LobbyCompletedEvent("lobby-1", "game-1", "initiator-1", "challenger-1",
                        "topic-1", false, java.time.Instant.now());

        fixture.given(opened, joined, completed)
                .when(new LobbyCommand.PurgeLobbyCommand("lobby-1"))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        LobbyEvent.LobbyPurgedEvent.class,
                        e -> ((LobbyEvent.LobbyPurgedEvent) e).lobbyId().equals("lobby-1")));
    }

    @Test
    void purgeLobby_leavesAggregateDeleted_soItCannotBeReloaded() {
        LobbyEvent.LobbyOpenedEvent opened =
                new LobbyEvent.LobbyOpenedEvent("lobby-1", "topic-1", "initiator-1", java.time.Instant.now());
        LobbyEvent.LobbyPurgedEvent purged =
                new LobbyEvent.LobbyPurgedEvent("lobby-1", java.time.Instant.now());

        fixture.given(opened, purged)
                .when(new LobbyCommand.JoinLobbyCommand("lobby-1", "challenger-1", LobbyParticipantType.HUMAN))
                .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
    }
}
