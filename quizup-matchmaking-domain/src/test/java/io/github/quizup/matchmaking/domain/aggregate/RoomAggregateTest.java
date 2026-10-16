package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.exception.RoomExceptions;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Test Axon in-memory de {@link RoomAggregate} (salle : deux humains ou humain/bot).
 * <p>
 * Le client émet une unique commande d'apparition ({@code JoinRoomCommand}, profil préparé par la
 * façade) : présence et enregistrement du second participant ; le bot, lui, est présent dès la
 * création de la salle (appliqué par l'agrégat, aucun join système).
 * Après l'apparition des deux joueurs, la préparation avance par commandes de phase (les résultats
 * préparent l'événement suivant : historique et questions) et la salle ne peut plus être quittée
 * (l'annulation reste possible). La clôture exige l'accusé de création de la partie
 * ({@code GAME_CREATED}).
 */
class RoomAggregateTest {

    private static final String ROOM_ID = "room-1";
    private static final String TOPIC = "topic-1";
    private static final String INITIATOR = "initiator-1";
    private static final String OPPONENT = "opponent-1";
    private static final String PARTICIPANT = "player-2";
    private static final String OTHER = "intruder";

    private static final RoomPlayer INITIATOR_PLAYER =
            new RoomPlayer(INITIATOR, "Alpha", Language.FR, 10, 100);
    private static final RoomPlayer OPPONENT_PLAYER =
            new RoomPlayer(OPPONENT, "Bravo", Language.EN, 12, 250);
    private static final RoomPlayer PARTICIPANT_PLAYER =
            new RoomPlayer(PARTICIPANT, "Charlie", Language.FR, 8, 50);
    private static final RoomPlayer OTHER_PLAYER =
            new RoomPlayer(OTHER, "Intruder", Language.FR, 1, 0);

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
    void initiatorJoin_marksPresenceOnly_andCarriesPreparedProfile() {
        fixture.given(created())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())
                                && INITIATOR_PLAYER.equals(((RoomEvent.RoomEnteredEvent) e).player())));
    }

    @Test
    void secondPlayerJoin_registersParticipantAndStartsReadyCheck() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT, PARTICIPANT_PLAYER))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> PARTICIPANT.equals(((RoomEvent.RoomEnteredEvent) e).playerId())))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomAllPlayersPresentEvent.class, e -> true));
    }

    @Test
    void joinTwice_isIdempotent() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER))
                .expectNoEvents();
    }

    @Test
    void nominalRoom_refusesNonInvitedPlayer() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OTHER, OTHER_PLAYER))
                .expectException(RoomExceptions.RoomNotInvitedProblem.class);
    }

    @Test
    void nominalRoom_letsInvitedPlayerJoin() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OPPONENT, OPPONENT_PLAYER))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> OPPONENT.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void botRoom_humanJoinCompletesPresence_andStartsPreparation() {
        fixture.given(createdBot(), enteredBot())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomAllPlayersPresentEvent.class, e -> true));
    }

    @Test
    void sharedRoom_refusesThirdPlayer() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OTHER, OTHER_PLAYER))
                .expectException(RoomExceptions.RoomAlreadyFullProblem.class);
    }

    @Test
    void playersLoaded_advancesPreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent())
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
    void playedQuestions_advancesPreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded())
                .when(new RoomCommand.MarkRoomPlayedQuestionsLoadedCommand(ROOM_ID, Set.of("q-1")))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomPlayedQuestionsLoadedEvent.class,
                        e -> ROOM_ID.equals(((RoomEvent.RoomPlayedQuestionsLoadedEvent) e).roomId())
                                && Set.of("q-1").equals(((RoomEvent.RoomPlayedQuestionsLoadedEvent) e).playedQuestionIds())));
    }

    @Test
    void playedQuestions_ignoredBeforePlayersLoaded() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent())
                .when(new RoomCommand.MarkRoomPlayedQuestionsLoadedCommand(ROOM_ID, Set.of("q-1")))
                .expectNoEvents();
    }

    @Test
    void questionsLoaded_advancesPreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded(),
                        playedQuestionsLoaded())
                .when(new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID, List.of()))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomQuestionsLoadedEvent.class,
                        e -> ROOM_ID.equals(((RoomEvent.RoomQuestionsLoadedEvent) e).roomId())));
    }

    @Test
    void questionsLoaded_ignoredBeforePlayersLoaded() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent())
                .when(new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID, List.of()))
                .expectNoEvents();
    }

    @Test
    void leave_clearsPresenceBeforePreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomLeftEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomLeftEvent) e).playerId())));
    }

    @Test
    void rejoin_afterLeaveIsAllowedBeforePreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER),
                        new RoomEvent.RoomLeftEvent(ROOM_ID, INITIATOR, Instant.now()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void leave_refusedOncePreparationStarted() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent())
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
    void gameCreated_advancesPreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded(),
                        playedQuestionsLoaded(), questionsLoaded())
                .when(new RoomCommand.MarkRoomGameCreatedCommand(ROOM_ID, "game-1"))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomGameCreatedEvent.class,
                        e -> "game-1".equals(((RoomEvent.RoomGameCreatedEvent) e).gameId())));
    }

    @Test
    void gameCreated_ignoredBeforeQuestionsLoaded() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded())
                .when(new RoomCommand.MarkRoomGameCreatedCommand(ROOM_ID, "game-1"))
                .expectNoEvents();
    }

    @Test
    void complete_closesRoomWithoutImmediatePurge() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded(),
                        playedQuestionsLoaded(), questionsLoaded(), gameCreated())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCompletedEvent.class,
                        e -> "game-1".equals(((RoomEvent.RoomCompletedEvent) e).gameId())));
    }

    @Test
    void complete_ignoredWithoutGameCreatedAck() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded(),
                        playedQuestionsLoaded(), questionsLoaded())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectNoEvents();
    }

    @Test
    void complete_refusedWhenBothPlayersNotPresent() {
        fixture.given(created(), gameCreated())
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
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER), allPresent(), playersLoaded())
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomCancelledEvent.class, e -> true));
    }

    @Test
    void cancel_refusedToSecondPlayer() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER))
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, PARTICIPANT))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    @Test
    void fail_setsFailed() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER))
                .when(new RoomCommand.FailRoomCommand(ROOM_ID, "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        RoomEvent.RoomFailedEvent.class,
                        e -> "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"
                                .equals(((RoomEvent.RoomFailedEvent) e).reason())));
    }

    @Test
    void purgeRoom_leavesAggregateDeleted_soItCannotBeReloaded() {
        fixture.given(created(), new RoomEvent.RoomPurgedEvent(ROOM_ID, Instant.now()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT, PARTICIPANT_PLAYER))
                .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
    }

    @Test
    void wizz_notifiesAbsentNominalOpponent() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomWizzedEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomWizzedEvent) e).playerId())
                                && OPPONENT.equals(((RoomEvent.RoomWizzedEvent) e).targetId())));
    }

    @Test
    void wizz_isSymmetric_earlyGuestNudgesAbsentInitiator() {
        fixture.given(createdWithOpponent(), entered(OPPONENT_PLAYER))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomWizzedEvent.class,
                        e -> OPPONENT.equals(((RoomEvent.RoomWizzedEvent) e).playerId())
                                && INITIATOR.equals(((RoomEvent.RoomWizzedEvent) e).targetId())));
    }

    @Test
    void wizz_refusedWhenRequesterNotPresent() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomWizzRequesterNotPresentProblem.class);
    }

    @Test
    void wizz_refusedWithoutNamedOpponent() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomWizzUnavailableProblem.class);
    }

    @Test
    void wizz_refusedToOutsider() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, OTHER))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    @Test
    void wizz_refusedWithinCooldown() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER),
                        new RoomEvent.RoomWizzedEvent(ROOM_ID, INITIATOR, OPPONENT, Instant.now()))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomWizzCooldownProblem.class);
    }

    @Test
    void wizz_allowedOnceCooldownElapsed() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER),
                        new RoomEvent.RoomWizzedEvent(ROOM_ID, INITIATOR, OPPONENT,
                                Instant.now().minusSeconds(11)))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomWizzedEvent.class,
                        e -> INITIATOR.equals(((RoomEvent.RoomWizzedEvent) e).playerId())));
    }

    @Test
    void wizz_ignoredOncePreparationStarted() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER), entered(OPPONENT_PLAYER), allPresent())
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    @Test
    void wizz_ignoredWhenRoomClosed() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER),
                        new RoomEvent.RoomCancelledEvent(ROOM_ID, INITIATOR, "PLAYER_CANCELLED", Instant.now()))
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    @Test
    void wizz_botRoom_neverWizzable() {
        // Salle bot : le bot est présent dès la création, l'humain qui apparaît engage la
        // préparation dans la même unité de travail → plus rien à relancer.
        fixture.given(createdBot(), enteredBot(), entered(INITIATOR_PLAYER), allPresent())
                .when(new RoomCommand.WizzRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
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

    private static RoomEvent.RoomEnteredEvent entered(RoomPlayer player) {
        return new RoomEvent.RoomEnteredEvent(ROOM_ID, player.playerId(), player, Instant.now());
    }

    private static RoomEvent.RoomEnteredEvent enteredBot() {
        return new RoomEvent.RoomEnteredEvent(ROOM_ID, QuizUpConstants.SYSTEM_USER_ID, null, Instant.now());
    }

    private static RoomEvent.RoomAllPlayersPresentEvent allPresent() {
        return new RoomEvent.RoomAllPlayersPresentEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayersLoadedEvent playersLoaded() {
        return new RoomEvent.RoomPlayersLoadedEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayedQuestionsLoadedEvent playedQuestionsLoaded() {
        return new RoomEvent.RoomPlayedQuestionsLoadedEvent(ROOM_ID, Set.of("q-1"), Instant.now());
    }

    private static RoomEvent.RoomQuestionsLoadedEvent questionsLoaded() {
        return new RoomEvent.RoomQuestionsLoadedEvent(ROOM_ID, List.of(), Instant.now());
    }

    private static RoomEvent.RoomGameCreatedEvent gameCreated() {
        return new RoomEvent.RoomGameCreatedEvent(ROOM_ID, "game-1", Instant.now());
    }
}
