package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.game.domain.model.PlayerProgressSnapshot;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
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
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de la salle temps réel (deux humains).
 * <ul>
 *   <li>{@code LobbyCreatedEvent} → expiration du salon (1 jour) : seule borne tant que la partie
 *       n'est pas lancée.</li>
 *   <li>{@code LobbyJoinedEvent} → l'expiration reste la borne courante du salon.</li>
 *   <li>{@code LobbyAllPlayersPresentEvent} → compte à rebours de lancement ({@code READY_CHECK}, 3 s).</li>
 *   <li>Fin du compte à rebours → création de la partie (2 humains) puis clôture de la salle.</li>
 *   <li>{@code LobbyLeftEvent} (sortie non destructive) → compte à rebours annulé ; retour possible
 *       jusqu'à l'expiration du salon.</li>
 *   <li>Un joueur hors ligne → {@code MissLobby} (présence) ; expiration → {@code ExpireLobby}.</li>
 *   <li>États terminaux → purge après rétention ; {@code LobbyPurgedEvent} termine la saga.</li>
 * </ul>
 * Aucune notion de bot ici.
 */
@Saga
@ProcessingGroup("lobby-saga")
public class LobbySaga {

    private static final Logger logger = LoggerFactory.getLogger(LobbySaga.class);

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Autowired
    private transient ProfileRepositoryPort profileRepositoryPort;

    @Getter
    @Setter
    private String lobbyId;

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

    @StartSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCreatedEvent event) {
        this.lobbyId = event.lobbyId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
        this.expiryDeadlineId = deadlineManager.schedule(
                LobbyDeadline.LOBBY_EXPIRY_DURATION,
                LobbyDeadline.LOBBY_EXPIRY);
        logger.info("Salle créée, expiration du lien dans {}h: lobbyId={}",
                LobbyDeadline.LOBBY_EXPIRY_DURATION.toHours(), lobbyId);
    }

    /**
     * Salon rejoint : l'expiration du salon ({@code LOBBY_EXPIRY_DURATION}) reste la seule borne
     * tant que la partie n'est pas lancée ; elle est annulée par les états terminaux.
     */
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.participantId = event.participantId();
        logger.info("Salon rejoint: lobbyId={}, participantId={}", lobbyId, event.participantId());
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyAllPlayersPresentEvent event) {
        if (readyDeadlineId == null) {
            readyDeadlineId = deadlineManager.schedule(
                    LobbyDeadline.LOBBY_READY_CHECK_DURATION,
                    LobbyDeadline.LOBBY_READY_CHECK);
        }
        logger.info("Les deux joueurs sont présents, lancement dans {}s: lobbyId={}",
                LobbyDeadline.LOBBY_READY_CHECK_DURATION.toSeconds(), lobbyId);
    }

    /**
     * Sortie d'un joueur (non destructive) : le compte à rebours de lancement est annulé ; le
     * salon reste ouvert jusqu'à son expiration, le joueur peut revenir entre-temps.
     */
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyLeftEvent event) {
        cancelReadyDeadline();
        logger.info("Joueur sorti de la salle (retour possible jusqu'à l'expiration): lobbyId={}, playerId={}",
                lobbyId, event.playerId());
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.LOBBY_READY_CHECK)
    public void onReadyCheckExpired() {
        createGameAndComplete();
    }

    private void createGameAndComplete() {
        LobbyPlayer initiator = profileRepositoryPort.getById(initiatorId);
        LobbyPlayer participant = profileRepositoryPort.getById(participantId);

        String gameId = UUID.randomUUID().toString();
        try {
            commandGateway.sendAndWait(new GameCommand.CreateGameCommand(
                    gameId,
                    topicId,
                    initiator.playerId(),
                    initiator.playerName(),
                    participant.playerId(),
                    participant.playerName(),
                    languagesOf(initiator, participant),
                    GamePlayerType.HUMAN,
                    null,
                    new PlayerProgressSnapshot(initiator.level(), initiator.xpTotal()),
                    new PlayerProgressSnapshot(participant.level(), participant.xpTotal())));
            commandGateway.send(new LobbyCommand.CompleteLobbyCommand(lobbyId, gameId));
            logger.info("Partie créée depuis la salle: lobbyId={}, gameId={}", lobbyId, gameId);
        } catch (Exception exception) {
            logger.error("Échec de création de partie depuis la salle: lobbyId={}", lobbyId, exception);
            commandGateway.send(new LobbyCommand.FailLobbyCommand(lobbyId, "GAME_CREATION_FAILED"));
        }
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.LOBBY_EXPIRY)
    public void onExpiry() {
        logger.info("Expiration du salon: lobbyId={}", lobbyId);
        commandGateway.send(new LobbyCommand.ExpireLobbyCommand(lobbyId));
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyDeclinedEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyExpiredEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyFailedEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyMissedEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        cancelReadyDeadline();
        schedulePurge();
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.LOBBY_PURGE)
    public void onPurge() {
        commandGateway.send(new LobbyCommand.PurgeLobbyCommand(lobbyId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        cancelAllDeadlines();
        logger.info("Saga salon terminée (purgé): lobbyId={}", lobbyId);
    }

    private void schedulePurge() {
        if (purgeDeadlineId == null) {
            purgeDeadlineId = deadlineManager.schedule(
                    LobbyDeadline.LOBBY_RETENTION_DURATION,
                    LobbyDeadline.LOBBY_PURGE);
        }
    }

    private void cancelAllDeadlines() {
        cancelExpiryDeadline();
        cancelReadyDeadline();
        if (purgeDeadlineId != null) {
            deadlineManager.cancelSchedule(LobbyDeadline.LOBBY_PURGE, purgeDeadlineId);
            purgeDeadlineId = null;
        }
    }

    private void cancelExpiryDeadline() {
        if (expiryDeadlineId != null) {
            deadlineManager.cancelSchedule(LobbyDeadline.LOBBY_EXPIRY, expiryDeadlineId);
            expiryDeadlineId = null;
        }
    }

    private void cancelReadyDeadline() {
        if (readyDeadlineId != null) {
            deadlineManager.cancelSchedule(LobbyDeadline.LOBBY_READY_CHECK, readyDeadlineId);
            readyDeadlineId = null;
        }
    }

    private static Set<Language> languagesOf(LobbyPlayer... players) {
        Set<Language> languages = new HashSet<>();
        for (LobbyPlayer player : players) {
            if (player != null && player.language() != null) {
                languages.add(player.language());
            }
        }
        return languages;
    }
}
