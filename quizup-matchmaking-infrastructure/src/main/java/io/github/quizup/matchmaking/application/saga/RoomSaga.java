package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.game.domain.model.GameQuestion;
import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.game.domain.model.PlayerProgressSnapshot;
import io.github.quizup.matchmaking.application.service.QuestionDrawService;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.RoomDeadline;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de la salle temps réel (deux humains).
 * <ul>
 *   <li>{@code RoomCreatedEvent} → expiration de la salle (1 jour).</li>
 *   <li>{@code RoomEnteredEvent} → mémorise le participant (apparition client-driven).</li>
 *   <li>{@code RoomAllPlayersPresentEvent} → <b>prefetch</b> profils + questions pendant le
 *       compte à rebours ; questions insuffisantes → échec immédiat de la salle.</li>
 *   <li>Fin du compte à rebours → création de la partie (questions fournies dans la commande)
 *       puis clôture de la salle ; échec → {@code RoomFailedEvent} annoncé aux joueurs présents.</li>
 *   <li>{@code RoomLeftEvent} (sortie non destructive) → compte à rebours annulé ; retour possible.</li>
 *   <li>États terminaux → purge après rétention ; {@code RoomPurgedEvent} termine la saga.</li>
 * </ul>
 * Aucune notion de bot ici.
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
    private transient QuestionDrawService questionDrawService;

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
    private String expiryDeadlineId;

    @Getter
    @Setter
    private String readyDeadlineId;

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
    private List<GameQuestion> questions;

    @StartSaga
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCreatedEvent event) {
        this.roomId = event.roomId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
        this.expiryDeadlineId = deadlineManager.schedule(
                RoomDeadline.ROOM_EXPIRY_DURATION,
                RoomDeadline.ROOM_EXPIRY);
        logger.info("Salle créée, expiration dans {}h: roomId={}",
                RoomDeadline.ROOM_EXPIRY_DURATION.toHours(), roomId);
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomEnteredEvent event) {
        if (!event.playerId().equals(initiatorId)) {
            this.participantId = event.playerId();
        }
        logger.info("Apparition en salle: roomId={}, playerId={}", roomId, event.playerId());
    }

    /**
     * Les deux joueurs sont présents : démarre le compte à rebours et prépare la partie
     * (profils + questions) pendant la fenêtre de 3 s. L'échec de préparation est annoncé
     * immédiatement aux joueurs présents.
     */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        if (readyDeadlineId == null) {
            readyDeadlineId = deadlineManager.schedule(
                    RoomDeadline.ROOM_READY_CHECK_DURATION,
                    RoomDeadline.ROOM_READY_CHECK);
        }
        logger.info("Les deux joueurs sont présents, lancement dans {}s: roomId={}",
                RoomDeadline.ROOM_READY_CHECK_DURATION.toSeconds(), roomId);
        prepareGameIfNeeded();
    }

    private void prepareGameIfNeeded() {
        if (questions != null) {
            return;
        }
        RoomPlayer initiator = profileRepositoryPort.getById(initiatorId);
        RoomPlayer participant = profileRepositoryPort.getById(participantId);
        List<GameQuestion> drawn = questionDrawService.draw(topicId, languagesOf(initiator, participant));
        if (drawn.size() < GameRules.TOTAL_ROUNDS) {
            logger.warn("Thème insuffisant dans les langues requises, échec de la salle: roomId={}, topicId={}, questions={}",
                    roomId, topicId, drawn.size());
            commandGateway.send(new RoomCommand.FailRoomCommand(roomId, "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"));
            return;
        }
        this.initiatorProfile = initiator;
        this.participantProfile = participant;
        this.questions = drawn;
    }

    /**
     * Sortie d'un joueur (non destructive) : le compte à rebours de lancement est annulé ; la
     * salle reste ouverte jusqu'à son expiration, le joueur peut revenir entre-temps.
     */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomLeftEvent event) {
        cancelReadyDeadline();
        logger.info("Joueur sorti de la salle (retour possible jusqu'à l'expiration): roomId={}, playerId={}",
                roomId, event.playerId());
    }

    @DeadlineHandler(deadlineName = RoomDeadline.ROOM_READY_CHECK)
    public void onReadyCheckExpired() {
        createGameAndComplete();
    }

    private void createGameAndComplete() {
        prepareGameIfNeeded();
        if (initiatorProfile == null || participantProfile == null
                || questions == null || questions.size() < GameRules.TOTAL_ROUNDS) {
            // Échec déjà envoyé par la préparation (ou préparation impossible).
            return;
        }

        String gameId = UUID.randomUUID().toString();
        try {
            commandGateway.sendAndWait(new GameCommand.CreateGameCommand(
                    gameId,
                    topicId,
                    initiatorProfile.playerId(),
                    initiatorProfile.playerName(),
                    participantProfile.playerId(),
                    participantProfile.playerName(),
                    GamePlayerType.HUMAN,
                    null,
                    new PlayerProgressSnapshot(initiatorProfile.level(), initiatorProfile.xpTotal()),
                    new PlayerProgressSnapshot(participantProfile.level(), participantProfile.xpTotal()),
                    questions));
            commandGateway.send(new RoomCommand.CompleteRoomCommand(roomId, gameId));
            logger.info("Partie créée depuis la salle: roomId={}, gameId={}", roomId, gameId);
        } catch (Exception exception) {
            logger.error("Échec de création de partie depuis la salle: roomId={}", roomId, exception);
            commandGateway.send(new RoomCommand.FailRoomCommand(roomId, "GAME_CREATION_FAILED"));
        }
    }

    @DeadlineHandler(deadlineName = RoomDeadline.ROOM_EXPIRY)
    public void onExpiry() {
        logger.info("Expiration de la salle: roomId={}", roomId);
        commandGateway.send(new RoomCommand.ExpireRoomCommand(roomId));
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCancelledEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomExpiredEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomFailedEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomCompletedEvent event) {
        cancelReadyDeadline();
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
        cancelReadyDeadline();
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

    private void cancelReadyDeadline() {
        if (readyDeadlineId != null) {
            deadlineManager.cancelSchedule(RoomDeadline.ROOM_READY_CHECK, readyDeadlineId);
            readyDeadlineId = null;
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
