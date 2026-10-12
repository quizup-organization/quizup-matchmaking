package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.matchmaking.application.service.GamePreparationService;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionChoice;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionDifficulty;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import io.github.quizup.matchmaking.domain.port.out.PlayedQuestionsPort;
import io.github.quizup.matchmaking.domain.port.out.QuestionDrawPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.SagaTestFixture;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test in-memory de l'orchestration asynchrone de {@link RoomSaga} via {@link SagaTestFixture} :
 * profils portés par les événements d'apparition (aucune I/O), historique et tirage ramenés par
 * commande → événement (jamais de {@code join()} sur le thread du processor, jamais de mutation
 * de la saga depuis le callback).
 */
class RoomSagaTest {

    private static final String ROOM_ID = "room-1";
    private static final String TOPIC = "topic-1";
    private static final String INITIATOR = "initiator-1";
    private static final String PARTICIPANT = "player-2";

    private static final RoomPlayer INITIATOR_PLAYER =
            new RoomPlayer(INITIATOR, "Alpha", Language.FR, 10, 100);
    private static final RoomPlayer PARTICIPANT_PLAYER =
            new RoomPlayer(PARTICIPANT, "Bravo", Language.EN, 12, 250);

    private final PlayedQuestionsPort playedQuestionsPort = Mockito.mock(PlayedQuestionsPort.class);
    private final QuestionDrawPort questionDrawPort = Mockito.mock(QuestionDrawPort.class);

    private final SagaTestFixture<RoomSaga> fixture = new SagaTestFixture<>(RoomSaga.class);

    RoomSagaTest() {
        fixture.registerResource(playedQuestionsPort);
        fixture.registerResource(questionDrawPort);
        fixture.registerResource(new GamePreparationService());
    }

    @Test
    void allPlayersPresent_marksPlayersLoadedWithoutAnyQuery() {
        fixture.givenAPublished(roomCreated())
                .andThenAPublished(entered(INITIATOR_PLAYER))
                .andThenAPublished(entered(PARTICIPANT_PLAYER))
                .whenPublishingA(allPresent())
                .expectDispatchedCommands(new RoomCommand.MarkRoomPlayersLoadedCommand(ROOM_ID));

        verifyNoInteractions(playedQuestionsPort, questionDrawPort);
    }

    @Test
    void playersLoaded_resolvesPlayedQuestionsWithNonBlockingCallback() {
        when(playedQuestionsPort.findPlayedQuestionIds(eq(TOPIC), anySet()))
                .thenReturn(CompletableFuture.completedFuture(Set.of("q-1")));

        fixture.givenAPublished(roomCreated())
                .andThenAPublished(entered(INITIATOR_PLAYER))
                .andThenAPublished(entered(PARTICIPANT_PLAYER))
                .andThenAPublished(allPresent())
                .whenPublishingA(playersLoaded())
                .expectDispatchedCommands(
                        new RoomCommand.MarkRoomPlayedQuestionsLoadedCommand(ROOM_ID, Set.of("q-1")));
    }

    @Test
    void playersLoaded_whenQueryFails_failsRoomInsteadOfBlocking() {
        when(playedQuestionsPort.findPlayedQuestionIds(eq(TOPIC), anySet()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("bus down")));

        fixture.givenAPublished(roomCreated())
                .andThenAPublished(entered(INITIATOR_PLAYER))
                .andThenAPublished(entered(PARTICIPANT_PLAYER))
                .andThenAPublished(allPresent())
                .whenPublishingA(playersLoaded())
                .expectDispatchedCommands(new RoomCommand.FailRoomCommand(ROOM_ID, "ROOM_PREPARATION_FAILED"));
    }

    @Test
    void playedQuestionsLoaded_drawsQuestions_withLevelDerivedPreferences() {
        DrawnQuestion question = drawnQuestion("q-1");
        when(questionDrawPort.draw(eq(TOPIC), eq(Set.of(Language.FR, Language.EN)),
                eq(DrawnQuestionDifficulty.MEDIUM), eq(Set.of("q-1"))))
                .thenReturn(CompletableFuture.completedFuture(List.of(question)));

        fixture.givenAPublished(roomCreated())
                .andThenAPublished(entered(INITIATOR_PLAYER))
                .andThenAPublished(entered(PARTICIPANT_PLAYER))
                .whenPublishingA(playedQuestionsLoaded(Set.of("q-1")))
                .expectDispatchedCommands(
                        new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID, List.of(question)));
    }

    @Test
    void questionsLoaded_withEnoughQuestions_sendsCreateGameCommand() {
        fixture.givenAPublished(roomCreated())
                .andThenAPublished(entered(INITIATOR_PLAYER))
                .andThenAPublished(entered(PARTICIPANT_PLAYER))
                .whenPublishingA(questionsLoaded(sevenQuestions()))
                .expectDispatchedCommandsMatching(createGameCommandMatching());
    }

    @Test
    void questionsLoaded_withInsufficientQuestions_failsRoom() {
        fixture.givenAPublished(roomCreated())
                .andThenAPublished(entered(INITIATOR_PLAYER))
                .andThenAPublished(entered(PARTICIPANT_PLAYER))
                .whenPublishingA(questionsLoaded(List.of(drawnQuestion("q-1"))))
                .expectDispatchedCommands(
                        new RoomCommand.FailRoomCommand(ROOM_ID, "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"));
    }

    private static Matcher<? extends List<? super CommandMessage<?>>> createGameCommandMatching() {
        return Matchers.matches(commands -> commands.size() == 1
                && commands.getFirst() instanceof CommandMessage<?> message
                && message.getPayload() instanceof GameCommand.CreateGameCommand command
                && TOPIC.equals(command.topicId())
                && command.questions().size() == GameRules.TOTAL_ROUNDS);
    }

    private static List<DrawnQuestion> sevenQuestions() {
        return java.util.stream.IntStream.range(0, GameRules.TOTAL_ROUNDS)
                .mapToObj(index -> drawnQuestion("q-" + index))
                .toList();
    }

    private static DrawnQuestion drawnQuestion(String questionId) {
        return new DrawnQuestion(questionId, Map.of(), null, "EASY", DrawnQuestionChoice.A);
    }

    private static RoomEvent.RoomCreatedEvent roomCreated() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID, TOPIC, INITIATOR, null, false, null,
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static RoomEvent.RoomEnteredEvent entered(RoomPlayer player) {
        return new RoomEvent.RoomEnteredEvent(ROOM_ID, player.playerId(), player, Instant.now());
    }

    private static RoomEvent.RoomAllPlayersPresentEvent allPresent() {
        return new RoomEvent.RoomAllPlayersPresentEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayersLoadedEvent playersLoaded() {
        return new RoomEvent.RoomPlayersLoadedEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayedQuestionsLoadedEvent playedQuestionsLoaded(Set<String> playedQuestionIds) {
        return new RoomEvent.RoomPlayedQuestionsLoadedEvent(ROOM_ID, playedQuestionIds, Instant.now());
    }

    private static RoomEvent.RoomQuestionsLoadedEvent questionsLoaded(List<DrawnQuestion> questions) {
        return new RoomEvent.RoomQuestionsLoadedEvent(ROOM_ID, questions, Instant.now());
    }
}
