package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.BotDifficulty;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.game.domain.model.GameQuestion;
import io.github.quizup.game.domain.model.GameQuestionChoice;
import io.github.quizup.game.domain.model.GameQuestionContent;
import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.game.domain.model.PlayerProgressSnapshot;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionChoice;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionContent;
import io.github.quizup.matchmaking.domain.model.RoomDeadline;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.QuestionDrawPort;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de la salle temps réel — seul créateur de la partie (deux humains ou humain/bot).
 * <ul>
 *   <li>{@code RoomCreatedEvent} → expiration de la salle (1 jour). La présence du bot
 *       ({@code vsBot}) est appliquée par l'agrégat à la création (aucun join système côté saga).</li>
 *   <li>{@code RoomEnteredEvent} → mémorise le participant (apparition client-driven).</li>
 *   <li>{@code RoomAllPlayersPresentEvent} → démarre la préparation : étape <b>profils</b>
 *       ({@link ProfileRepositoryPort}, bot exclu) puis étape <b>questions</b>
 *       ({@link QuestionDrawPort}, vérification du sujet n°2) ; chaque étape avance le statut de
 *       la salle. Questions insuffisantes → échec immédiat de la salle.</li>
 *   <li>{@code RoomQuestionsLoadedEvent} → fin de préparation : création immédiate de la partie
 *       (plus de compte à rebours de lancement).</li>
 *   <li>Création de la partie (questions fournies dans la commande)
 *       puis clôture de la salle ; échec → {@code RoomFailedEvent} annoncé aux joueurs présents.</li>
 *   <li>{@code RoomLeftEvent} (sortie non destructive avant engagement) → retour possible. La
 *       sortie est refusée par l'agrégat une fois la préparation
 *       engagée.</li>
 *   <li>États terminaux → purge après rétention ; {@code RoomPurgedEvent} termine la saga.</li>
 * </ul>
 */
@Saga
@ProcessingGroup("room-saga")
public class RoomSaga {

    private static final Logger logger = LoggerFactory.getLogger(RoomSaga.class);

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Autowired
    private transient ProfileRepositoryPort profileRepositoryPort;

    @Autowired
    private transient QuestionDrawPort questionDrawPort;

    @Getter
    @Setter
    private String roomId;

    @Getter
    @Setter
    private String topicId;

    @Getter
    @Setter
    private String initiatorId;

    @Getter
    @Setter
    private String participantId;

    @Getter
    @Setter
    private boolean vsBot;

    @Getter
    @Setter
    private String botDifficulty;

    @Getter
    @Setter
    private String expiryDeadlineId;

    @Getter
    @Setter
    private String purgeDeadlineId;

    @Getter
    @Setter
    private RoomPlayer initiatorProfile;

    @Getter
    @Setter
    private RoomPlayer participantProfile;

    @Getter
    @Setter
    private List<DrawnQuestion> drawnQuestions;

    @StartSaga
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCreatedEvent event) {
        this.roomId = event.roomId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
        this.vsBot = event.vsBot();
        this.botDifficulty = event.botDifficulty();
        this.expiryDeadlineId = deadlineManager.schedule(
                RoomDeadline.ROOM_EXPIRY_DURATION,
                RoomDeadline.ROOM_EXPIRY);
        logger.info("Salle créée, expiration dans {}h: roomId={}, vsBot={}",
                RoomDeadline.ROOM_EXPIRY_DURATION.toHours(), roomId, vsBot);
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomEnteredEvent event) {
        // Seul le participant humain est mémorisé (le bot n'a pas de profil à résoudre).
        if (!vsBot && !event.playerId().equals(initiatorId)) {
            this.participantId = event.playerId();
        }
        logger.info("Apparition en salle: roomId={}, playerId={}", roomId, event.playerId());
    }

    /**
     * Les deux joueurs sont présents : lance la préparation (profils puis questions). La partie
     * sera créée dès la fin de la préparation ({@link RoomEvent.RoomQuestionsLoadedEvent}).
     */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        logger.info("Les deux joueurs sont présents, préparation de la partie: roomId={}", roomId);
        loadPlayers();
        loadQuestions();
    }

    /** Fin de préparation (questions tirées) : la partie est créée immédiatement. */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomQuestionsLoadedEvent event) {
        logger.info("Préparation terminée, création de la partie: roomId={}", roomId);
        createGameAndComplete();
    }

    /** Étape profils : le bot n'a pas de profil à résoudre. */
    private void loadPlayers() {
        if (playersLoaded()) {
            return;
        }
        this.initiatorProfile = profileRepositoryPort.getById(initiatorId);
        this.participantProfile = vsBot ? null : profileRepositoryPort.getById(participantId);
        commandGateway.send(new RoomCommand.MarkRoomPlayersLoadedCommand(roomId));
    }

    private boolean playersLoaded() {
        return initiatorProfile != null && (vsBot || participantProfile != null);
    }

    /** Étape questions : tirage dans les langues des joueurs (vérification du sujet n°2). */
    private void loadQuestions() {
        if (drawnQuestions != null || !playersLoaded()) {
            return;
        }
        List<DrawnQuestion> drawn = questionDrawPort.draw(topicId,
                languagesOf(initiatorProfile, participantProfile));
        if (drawn.size() < GameRules.TOTAL_ROUNDS) {
            logger.warn("Thème insuffisant dans les langues requises, échec de la salle: roomId={}, topicId={}, questions={}",
                    roomId, topicId, drawn.size());
            commandGateway.send(new RoomCommand.FailRoomCommand(roomId, "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"));
            return;
        }
        this.drawnQuestions = drawn;
        commandGateway.send(new RoomCommand.MarkRoomQuestionsLoadedCommand(roomId));
    }

    /**
     * Sortie d'un joueur avant engagement (non destructive) : la salle reste ouverte jusqu'à son
     * expiration, le joueur peut revenir. La préparation n'est pas engagée (sortie possible
     * uniquement en {@code WAITING_PLAYERS}).
     */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomLeftEvent event) {
        logger.info("Joueur sorti de la salle (retour possible jusqu'à l'expiration): roomId={}, playerId={}",
                roomId, event.playerId());
    }

    /**
     * Fin de préparation : la partie est créée seulement si la préparation a abouti (sinon
     * l'échec a déjà été annoncé et la salle est terminale).
     */
    private void createGameAndComplete() {
        if (!playersLoaded() || drawnQuestions == null || drawnQuestions.size() < GameRules.TOTAL_ROUNDS) {
            return;
        }

        BotDifficulty difficulty = vsBot ? parseBotDifficulty(botDifficulty) : null;
        String gameId = UUID.randomUUID().toString();
        try {
            commandGateway.sendAndWait(new GameCommand.CreateGameCommand(
                    gameId,
                    topicId,
                    initiatorProfile.playerId(),
                    initiatorProfile.playerName(),
                    vsBot ? QuizUpConstants.SYSTEM_USER_ID : participantProfile.playerId(),
                    vsBot ? QuizUpConstants.SYSTEM_USER_NAME : participantProfile.playerName(),
                    vsBot ? GamePlayerType.BOT : GamePlayerType.HUMAN,
                    difficulty,
                    new PlayerProgressSnapshot(initiatorProfile.level(), initiatorProfile.xpTotal()),
                    vsBot
                            ? PlayerProgressSnapshot.forBot(difficulty)
                            : new PlayerProgressSnapshot(participantProfile.level(), participantProfile.xpTotal()),
                    toGameQuestions(drawnQuestions)));
            commandGateway.send(new RoomCommand.CompleteRoomCommand(roomId, gameId));
            logger.info("Partie créée depuis la salle: roomId={}, gameId={}, vsBot={}", roomId, gameId, vsBot);
        } catch (Exception exception) {
            logger.error("Échec de création de partie depuis la salle: roomId={}", roomId, exception);
            commandGateway.send(new RoomCommand.FailRoomCommand(roomId, "GAME_CREATION_FAILED"));
        }
    }

    private static BotDifficulty parseBotDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return BotDifficulty.NORMAL;
        }
        try {
            return BotDifficulty.valueOf(difficulty);
        } catch (IllegalArgumentException exception) {
            return BotDifficulty.NORMAL;
        }
    }

    @DeadlineHandler(deadlineName = RoomDeadline.ROOM_EXPIRY)
    public void onExpiry() {
        logger.info("Expiration de la salle: roomId={}", roomId);
        commandGateway.send(new RoomCommand.ExpireRoomCommand(roomId));
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCancelledEvent event) {
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomExpiredEvent event) {
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomFailedEvent event) {
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCompletedEvent event) {
        schedulePurge();
    }

    @DeadlineHandler(deadlineName = RoomDeadline.ROOM_PURGE)
    public void onPurge() {
        commandGateway.send(new RoomCommand.PurgeRoomCommand(roomId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomPurgedEvent event) {
        cancelAllDeadlines();
        logger.info("Saga salle terminée (purgée): roomId={}", roomId);
    }

    private void schedulePurge() {
        if (purgeDeadlineId == null) {
            purgeDeadlineId = deadlineManager.schedule(
                    RoomDeadline.ROOM_RETENTION_DURATION,
                    RoomDeadline.ROOM_PURGE);
        }
    }

    private void cancelAllDeadlines() {
        cancelExpiryDeadline();
        if (purgeDeadlineId != null) {
            deadlineManager.cancelSchedule(RoomDeadline.ROOM_PURGE, purgeDeadlineId);
            purgeDeadlineId = null;
        }
    }

    private void cancelExpiryDeadline() {
        if (expiryDeadlineId != null) {
            deadlineManager.cancelSchedule(RoomDeadline.ROOM_EXPIRY, expiryDeadlineId);
            expiryDeadlineId = null;
        }
    }

    private static Set<Language> languagesOf(RoomPlayer... players) {
        Set<Language> languages = new HashSet<>();
        for (RoomPlayer player : players) {
            if (player != null && player.language() != null) {
                languages.add(player.language());
            }
        }
        return languages;
    }

    // ── Mapping du modèle local vers le domaine game (l'émetteur de la commande mappe lui-même :
    // ── un *-domain ne dépend jamais d'un autre service, cf. command-preparation.md) ──

    private static List<GameQuestion> toGameQuestions(List<DrawnQuestion> questions) {
        return questions.stream().map(RoomSaga::toGameQuestion).toList();
    }

    private static GameQuestion toGameQuestion(DrawnQuestion question) {
        Map<Language, GameQuestionContent> translations = new EnumMap<>(Language.class);
        question.translations().forEach((language, content) ->
                translations.put(language, toGameQuestionContent(content)));
        return new GameQuestion(
                question.questionId(),
                translations,
                question.imageUrl(),
                question.difficulty(),
                toGameQuestionChoice(question.correctAnswer()));
    }

    private static GameQuestionContent toGameQuestionContent(DrawnQuestionContent content) {
        Map<GameQuestionChoice, String> answers = new EnumMap<>(GameQuestionChoice.class);
        content.answers().forEach((choice, text) -> {
            GameQuestionChoice mapped = toGameQuestionChoice(choice);
            if (mapped != null) {
                answers.put(mapped, text);
            }
        });
        return new GameQuestionContent(content.text(), answers);
    }

    private static GameQuestionChoice toGameQuestionChoice(DrawnQuestionChoice choice) {
        return choice == null ? null : GameQuestionChoice.valueOf(choice.name());
    }
}
