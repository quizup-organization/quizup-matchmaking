package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
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
 * Orchestration du salon privé (salle d'attente entre deux humains).
 * <ul>
 *   <li>{@code LobbyCreatedEvent} → planifie l'expiration (1 h).</li>
 *   <li>{@code LobbyJoinedEvent} → crée la partie (2 humains) puis purge le salon ; échec → {@code FailLobby}.</li>
 *   <li>annulé / expiré / échoué → purge après rétention ; {@code LobbyPurgedEvent} termine la saga.</li>
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
        logger.info("Salon privé créé, expiration dans {}h: lobbyId={}",
                LobbyDeadline.LOBBY_EXPIRY_DURATION.toHours(), lobbyId);
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.participantId = event.participantId();
        cancelExpiryDeadline();

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
                    null));
            commandGateway.send(new LobbyCommand.CompleteLobbyCommand(lobbyId, gameId));
            logger.info("Partie créée depuis le salon: lobbyId={}, gameId={}", lobbyId, gameId);
        } catch (Exception exception) {
            logger.error("Échec de création de partie depuis le salon: lobbyId={}", lobbyId, exception);
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
        cancelExpiryDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyDeclinedEvent event) {
        cancelExpiryDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyExpiredEvent event) {
        cancelExpiryDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyFailedEvent event) {
        cancelExpiryDeadline();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        cancelExpiryDeadline();
        schedulePurge();
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.LOBBY_PURGE)
    public void onPurge() {
        commandGateway.send(new LobbyCommand.PurgeLobbyCommand(lobbyId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        cancelDeadlines();
        logger.info("Saga salon terminée (purgé): lobbyId={}", lobbyId);
    }

    private void schedulePurge() {
        if (purgeDeadlineId == null) {
            purgeDeadlineId = deadlineManager.schedule(
                    LobbyDeadline.LOBBY_RETENTION_DURATION,
                    LobbyDeadline.LOBBY_PURGE);
        }
    }

    private void cancelDeadlines() {
        cancelExpiryDeadline();
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
