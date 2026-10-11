package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.exception.RoomExceptions;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * Test Axon in-memory de {@link RoomAggregate} (salle : deux humains ou humain/bot).
 * <p>
 * Le client émet une unique commande d'apparition ({@code JoinRoomCommand}) : présence et
 * enregistrement du second participant ; le bot, lui, est présent dès la création de la salle
 * (appliqué par l'agrégat, aucun join système).
 * Après l'apparition des deux joueurs, la préparation avance par commandes de phase et la
 * salle ne peut plus être quittée (l'annulation reste possible).
 */
class RoomAggregateTest {

    private static final String ROOM_ID = "room-1";
    private static final String TOPIC = "topic-1";
    private static final String INITIATOR = "initiator-1";
    private static final String OPPONENT = "opponent-1";
    private static final String PARTICIPANT = "player-2";
    private static final String OTHER = "intruder";

    private final AggregateTestFixture<RoomAggregate> fixture =
            new AggregateTestFixture<>(RoomAggregate.class);

    @Test
    void createRoom_appliesCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(ROOM_ID, TOPIC, INITIATOR, null, false, null))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCreatedEvent.class,
                        e -> ((RoomEvent.RoomCreatedEvent) e).roomId().equals(ROOM_ID)
                                && ((RoomEvent.RoomCreatedEvent) e).initiatorId().equals(INITIATOR)
                                && ((RoomEvent.RoomCreatedEvent) e).opponentId() == null
                                && !((RoomEvent.RoomCreatedEvent) e).vsBot()));
    }

    @Test
    void createNominalRoom_appliesCreatedEventWithOpponent() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(ROOM_ID, TOPIC, INITIATOR, OPPONENT, false, null))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCreatedEvent.class,
                        e -> OPPONENT.equals(((RoomEvent.RoomCreatedEvent) e).opponentId())));
    }

    @Test
    void createBotRoom_carriesDifficultyAndBotFlag_andAppliesBotPresence() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(
                        ROOM_ID, TOPIC, INITIATOR, QuizUpConstants.SYSTEM_USER_ID, true, "HARD"))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomCreatedEvent.class,
                        e -> ((RoomEvent.RoomCreatedEvent) e).vsBot()
                                && "HARD".equals(((RoomEvent.RoomCreatedEvent) e).botDifficulty())))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> QuizUpConstants.SYSTEM_USER_ID.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void initiatorJoin_marksPresenceOnly() {
        fixture.given(created())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void secondPlayerJoin_registersParticipantAndStartsReadyCheck() {
        fixture.given(created(), entered(INITIATOR))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> PARTICIPANT.equals(((RoomEvent.RoomEnteredEvent) e).playerId())))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomAllPlayersPresentEvent.class, e -> true));
    }

    @Test
    void joinTwice_isIdempotent() {
        fixture.given(created(), entered(INITIATOR))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    @Test
    void nominalRoom_refusesNonInvitedPlayer() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OTHER))
                .expectException(RoomExceptions.RoomNotInvitedProblem.class);
    }

    @Test
    void nominalRoom_letsInvitedPlayerJoin() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> OPPONENT.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void botRoom_humanJoinCompletesPresence_andStartsPreparation() {
        fixture.given(createdBot(), entered(QuizUpConstants.SYSTEM_USER_ID))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomAllPlayersPresentEvent.class, e -> true));
    }

    @Test
    void sharedRoom_refusesThirdPlayer() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OTHER))
                .expectException(RoomExceptions.RoomAlreadyFullProblem.class);
    }

    @Test
    void playersLoaded_advancesPreparation() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT), allPresent())
                .when(new RoomCommand.MarkRoomPlayersLoadedCommand(ROOM_ID))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomPlayersLoadedEvent.class,
                        e -> ROOM_ID.equals(((RoomEvent.RoomPlayersLoadedEvent) e).roomId())));
    }

    @Test
    void playersLoaded_ignoredWhenPlayersNotPresent() {
        fixture.given(created())
                .when(new RoomCommand.MarkRoomPlayersLoadedCommand(ROOM_ID))
                .expectNoEvents();
    }

    @Test
    void questionsLoaded_advancesPreparation() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT), allPresent(), playersLoaded())
                .when(new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomQuestionsLoadedEvent.class,
                        e -> ROOM_ID.equals(((RoomEvent.RoomQuestionsLoadedEvent) e).roomId())));
    }

    @Test
    void questionsLoaded_ignoredBeforePlayersLoaded() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT), allPresent())
                .when(new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID))
                .expectNoEvents();
    }

    @Test
    void leave_clearsPresenceBeforePreparation() {
        fixture.given(created(), entered(INITIATOR))
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomLeftEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomLeftEvent) e).playerId())));
    }

    @Test
    void rejoin_afterLeaveIsAllowedBeforePreparation() {
        fixture.given(created(), entered(INITIATOR),
                        new RoomEvent.RoomLeftEvent(ROOM_ID, INITIATOR, Instant.now()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void leave_refusedOncePreparationStarted() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT), allPresent())
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, PARTICIPANT))
                .expectException(RoomExceptions.RoomPreparationStartedProblem.class);
    }

    @Test
    void leave_whenAlreadyAbsent_isNoop() {
        fixture.given(created())
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    @Test
    void leave_refusedToNonParticipant() {
        fixture.given(created())
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, OTHER))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    @Test
    void complete_closesRoomWithoutImmediatePurge() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT), allPresent(), playersLoaded(),
                        questionsLoaded())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCompletedEvent.class,
                        e -> "game-1".equals(((RoomEvent.RoomCompletedEvent) e).gameId())));
    }

    @Test
    void complete_refusedWhenBothPlayersNotPresent() {
        fixture.given(created(), entered(INITIATOR))
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectException(RoomExceptions.ParticipantNotPresentProblem.class);
    }

    @Test
    void cancel_byInitiator_closesRoom() {
        fixture.given(created())
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomCancelledEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomCancelledEvent) e).initiatorId())));
    }

    @Test
    void cancel_remainsAllowedDuringPreparation() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT), allPresent(), playersLoaded())
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomCancelledEvent.class, e -> true));
    }

    @Test
    void cancel_refusedToSecondPlayer() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT))
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, PARTICIPANT))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    @Test
    void fail_setsFailed() {
        fixture.given(created(), entered(INITIATOR), entered(PARTICIPANT))
                .when(new RoomCommand.FailRoomCommand(ROOM_ID, "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomFailedEvent.class,
                        e -> "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"
                                .equals(((RoomEvent.RoomFailedEvent) e).reason())));
    }

    @Test
    void purgeRoom_leavesAggregateDeleted_soItCannotBeReloaded() {
        fixture.given(created(), new RoomEvent.RoomPurgedEvent(ROOM_ID, Instant.now()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT))
                .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
    }

    private static RoomEvent.RoomCreatedEvent created() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID, TOPIC, INITIATOR, null, false, null,
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static RoomEvent.RoomCreatedEvent createdWithOpponent() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID, TOPIC, INITIATOR, OPPONENT, false, null,
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static RoomEvent.RoomCreatedEvent createdBot() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID, TOPIC, INITIATOR, QuizUpConstants.SYSTEM_USER_ID, true, "NORMAL",
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static RoomEvent.RoomEnteredEvent entered(String playerId) {
        return new RoomEvent.RoomEnteredEvent(ROOM_ID, playerId, Instant.now());
    }

    private static RoomEvent.RoomAllPlayersPresentEvent allPresent() {
        return new RoomEvent.RoomAllPlayersPresentEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayersLoadedEvent playersLoaded() {
        return new RoomEvent.RoomPlayersLoadedEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomQuestionsLoadedEvent questionsLoaded() {
        return new RoomEvent.RoomQuestionsLoadedEvent(ROOM_ID, Instant.now());
    }
}
