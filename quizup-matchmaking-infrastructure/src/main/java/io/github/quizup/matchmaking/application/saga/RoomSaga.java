package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.event.GameEvent;
import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.matchmaking.application.service.GamePreparationService;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionDifficulty;
import io.github.quizup.matchmaking.domain.model.QuestionLevelRules;
import io.github.quizup.matchmaking.domain.model.RoomDeadline;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import io.github.quizup.matchmaking.domain.port.out.PlayedQuestionsPort;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.QuestionDrawPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de la salle temps réel — seul créateur de la partie (deux humains ou humain/bot).
 * <ul>
 *   <li>{@code RoomCreatedEvent} → expiration de la salle (1 jour). La présence du bot
 *       ({@code vsBot}) est appliquée par l'agrégat à la création (aucun join système côté saga).</li>
 *   <li>{@code RoomEnteredEvent} → mémorise le participant (apparition client-driven).</li>
 *   <li>{@code RoomAllPlayersPresentEvent} → étape <b>profils</b> ({@link ProfileRepositoryPort},
 *       bot exclu) ; {@code RoomPlayersLoadedEvent} → étape <b>historique des questions jouées</b>
 *       ({@link PlayedQuestionsPort}, quizup-game) ; {@code RoomPlayedQuestionsLoadedEvent} → étape
 *       <b>questions</b> ({@link QuestionDrawPort}, vérification du sujet n°2, préférences
 *       jamais-jouées + difficulté déduite du niveau) ; chaque étape avance le statut de la salle.
 *       Questions insuffisantes → échec immédiat de la salle.</li>
 *   <li>{@code RoomQuestionsLoadedEvent} → envoi de {@code CreateGameCommand} (assemblé par
 *       {@link GamePreparationService}) en <b>fire-and-forget</b>, association sur {@code gameId}
 *       et deadline d'accusé ; {@code GameCreatedEvent} → {@code MarkRoomGameCreatedCommand} puis
 *       {@code RoomGameCreatedEvent} → {@code CompleteRoomCommand}. Accusé absent ou échec
 *       synchrone → {@code RoomFailedEvent} annoncé aux joueurs présents.</li>
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

    @Autowired
    private transient PlayedQuestionsPort playedQuestionsPort;

    @Autowired
    private transient GamePreparationService gamePreparationService;

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
    private String gameId;

    @Getter
    @Setter
    private String gameCreationDeadlineId;

    @Getter
    @Setter
    private RoomPlayer initiatorProfile;

    @Getter
    @Setter
    private RoomPlayer participantProfile;

    @Getter
    @Setter
    private Set<String> playedQuestionIds;

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
     * Les deux joueurs sont présents : étape 1, résolution des profils. L'étape 2 (questions)
     * est déclenchée par {@link RoomEvent.RoomPlayersLoadedEvent} — un pas par événement.
     */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        logger.info("Les deux joueurs sont présents, résolution des profils: roomId={}", roomId);
        loadPlayers();
    }

    /** Profils résolus : étape 2, résolution de l'historique des questions jouées (quizup-game). */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomPlayersLoadedEvent event) {
        logger.info("Profils chargés, résolution de l'historique des questions: roomId={}", roomId);
        resolvePlayedQuestions();
    }

    /** Historique résolu : étape 3, tirage filtré des questions (vérification du sujet n°2). */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomPlayedQuestionsLoadedEvent event) {
        logger.info("Historique résolu, tirage des questions: roomId={}", roomId);
        loadQuestions();
    }

    /** Fin de préparation (questions tirées) : la création de la partie est demandée (send). */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomQuestionsLoadedEvent event) {
        logger.info("Préparation terminée, création de la partie: roomId={}", roomId);
        requestGameCreation();
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

    /** Étape historique : questions déjà jouées par les joueurs humains de la salle. */
    private void resolvePlayedQuestions() {
        if (playedQuestionIds != null || !playersLoaded()) {
            return;
        }
        this.playedQuestionIds = playedQuestionsPort.findPlayedQuestionIds(
                topicId, humanPlayerIds());
        commandGateway.send(new RoomCommand.MarkRoomPlayedQuestionsLoadedCommand(roomId));
    }

    /** Joueurs humains de la salle (le bot n'a pas d'historique). */
    private Set<String> humanPlayerIds() {
        Set<String> playerIds = new HashSet<>();
        playerIds.add(initiatorId);
        if (!vsBot && participantId != null) {
            playerIds.add(participantId);
        }
        return playerIds;
    }

    /** Étape questions : tirage filtré (jamais-jouées, difficulté déduite) — vérification du sujet n°2. */
    private void loadQuestions() {
        if (drawnQuestions != null || !playersLoaded()) {
            return;
        }
        DrawnQuestionDifficulty target = QuestionLevelRules.targetFor(
                initiatorProfile.level(),
                participantProfile == null ? null : participantProfile.level());
        List<DrawnQuestion> drawn = questionDrawPort.draw(
                topicId,
                languagesOf(initiatorProfile, participantProfile),
                target,
                playedQuestionIds == null ? Set.of() : playedQuestionIds);
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
     * Demande de création : {@code CreateGameCommand} fire-and-forget (assemblée par
     * {@link GamePreparationService}), association sur {@code gameId} pour recevoir l'accusé du
     * service game, et deadline de sécurité. Idempotent (relecture at-least-once).
     */
    private void requestGameCreation() {
        if (gameId != null
                || !playersLoaded()
                || drawnQuestions == null
                || drawnQuestions.size() < GameRules.TOTAL_ROUNDS) {
            return;
        }
        this.gameId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("gameId", gameId);
        this.gameCreationDeadlineId = deadlineManager.schedule(
                RoomDeadline.ROOM_GAME_CREATION_DURATION,
                RoomDeadline.ROOM_GAME_CREATION);
        logger.info("Création de partie demandée: roomId={}, gameId={}, vsBot={}", roomId, gameId, vsBot);
        commandGateway.send(gamePreparationService.createGameCommand(
                gameId, topicId, initiatorProfile, participantProfile, vsBot, botDifficulty, drawnQuestions));
    }

    /** Accusé du service game : la partie existe, la salle peut être clôturée. */
    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.GameCreatedEvent event) {
        cancelGameCreationDeadline();
        logger.info("Partie créée: roomId={}, gameId={}", roomId, event.gameId());
        commandGateway.send(new RoomCommand.MarkRoomGameCreatedCommand(roomId, event.gameId()));
    }

    /** La salle a accusé la création : clôture. */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomGameCreatedEvent event) {
        logger.info("Accusé de création en salle, clôture: roomId={}, gameId={}", roomId, event.gameId());
        commandGateway.send(new RoomCommand.CompleteRoomCommand(roomId, event.gameId()));
    }

    /** Accusé de création absent (échec/routage perdu) : la salle échoue au lieu de rester bloquée. */
    @DeadlineHandler(deadlineName = RoomDeadline.ROOM_GAME_CREATION)
    public void onGameCreationTimeout() {
        logger.warn("Accusé de création de partie absent: roomId={}, gameId={}", roomId, gameId);
        commandGateway.send(new RoomCommand.FailRoomCommand(roomId, "GAME_CREATION_TIMEOUT"));
    }

    /**
     * Échec synchrone d'un handler de la saga (profils, tirage, mapping) : la salle échoue au lieu
     * de bloquer le processor. Les échecs asynchrones des commandes {@code send} ne passent pas ici
     * (créations d'agrégats pures ; accusé manquant couvert par la deadline).
     */
    @ExceptionHandler
    public void on(Exception exception) {
        logger.error("Échec de préparation de la salle: roomId={}", roomId, exception);
        commandGateway.send(new RoomCommand.FailRoomCommand(roomId, "ROOM_PREPARATION_FAILED"));
    }

    @DeadlineHandler(deadlineName = RoomDeadline.ROOM_EXPIRY)
    public void onExpiry() {
        logger.info("Expiration de la salle: roomId={}", roomId);
        commandGateway.send(new RoomCommand.ExpireRoomCommand(roomId));
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCancelledEvent event) {
        cancelGameCreationDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomExpiredEvent event) {
        cancelGameCreationDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomFailedEvent event) {
        cancelGameCreationDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCompletedEvent event) {
        cancelGameCreationDeadline();
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
        cancelGameCreationDeadline();
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

    private void cancelGameCreationDeadline() {
        if (gameCreationDeadlineId != null) {
            deadlineManager.cancelSchedule(RoomDeadline.ROOM_GAME_CREATION, gameCreationDeadlineId);
            gameCreationDeadlineId = null;
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
}
