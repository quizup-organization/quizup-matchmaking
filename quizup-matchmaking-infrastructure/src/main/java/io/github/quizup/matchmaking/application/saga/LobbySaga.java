package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GameMode;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.model.LobbyPolicy;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * Orchestre le cycle de vie d'un lobby.
 * <p>
 * Responsabilité : orchestration uniquement.
 * Les règles métier (délai, fallback bot) sont dans LobbyPolicy.
 * Le mapping LobbyParticipantType → GamePlayerType se fait ici, à la frontière des bounded contexts.
 * <p>
 * Flow :
 * 1. LobbyOpenedEvent → planifie le timeout d'attente humain
 * 2a. LobbyJoinedEvent → annule le timeout, crée la partie, ferme le lobby
 * 2b. Timeout → LobbyPolicy → JoinLobbyCommand BOT
 * 3. LobbyCompletedEvent / LobbyCancelledEvent → planifie la **purge** après rétention
 * 4. Deadline purge → PurgeLobbyCommand (supprime l'agrégat + la projection)
 * 5. LobbyPurgedEvent → saga terminée
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
    private String challengerId;

    @Getter
    @Setter
    private LobbyPlayer initiatorProfile;

    @Getter
    @Setter
    private LobbyPlayer challengerProfile;

    @Getter
    @Setter
    private String matchmakingDeadlineId;

    @Getter
    @Setter
    private String purgeDeadlineId;

    @StartSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyOpenedEvent event) {
        this.lobbyId = event.lobbyId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
        this.initiatorProfile = profileRepositoryPort.getById(initiatorId);

        this.matchmakingDeadlineId = deadlineManager.schedule(
                LobbyDeadline.MATCHMAKING_DEADLINE_DURATION,
                LobbyDeadline.MATCHMAKING_DEADLINE
        );

        logger.info("Lobby ouvert, attente humain {}s: lobbyId={}", LobbyDeadline.MATCHMAKING_DEADLINE_DURATION.getSeconds(), lobbyId);
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.challengerId = event.challengerId();

        cancelMatchmakingDeadline();

        this.challengerProfile = profileRepositoryPort.getById(challengerId);

        String gameId = UUID.randomUUID().toString();

        GamePlayerType gamePlayerType = toGamePlayerType(event.challengerType());

        commandGateway.send(new GameCommand.CreateGameCommand(
                gameId,
                topicId,
                initiatorProfile.playerId(),
                initiatorProfile.playerName(),
                challengerProfile.playerId(),
                challengerProfile.playerName(),
                GameMode.SYNC,
                gamePlayerType,
                null,
                null
        ));

        commandGateway.send(new LobbyCommand.CompleteLobbyCommand(lobbyId, gameId));

        logger.info("Partie orchestrée: lobbyId={}, gameId={}, initiator={}, challenger={}", lobbyId, gameId, initiatorProfile.playerId(), challengerProfile.playerId());
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.MATCHMAKING_DEADLINE)
    public void onMatchmakingTimeout() {
        if (LobbyPolicy.shouldFallbackToBot(challengerId)) {
            logger.info("Timeout matchmaking — injection bot: lobbyId={}", lobbyId);
            commandGateway.send(new LobbyCommand.JoinLobbyCommand(lobbyId, LobbyPolicy.BOT_PLAYER_ID, LobbyParticipantType.BOT));
        }
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        logger.info("Lobby fermé (partie créée): lobbyId={}, gameId={}", lobbyId, event.gameId());
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        cancelMatchmakingDeadline();
        logger.info("Lobby annulé: lobbyId={}", lobbyId);
        schedulePurge();
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.LOBBY_PURGE)
    public void onPurgeDeadline() {
        logger.info("Rétention écoulée — purge du lobby: lobbyId={}", lobbyId);
        commandGateway.send(new LobbyCommand.PurgeLobbyCommand(lobbyId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        cancelDeadlines();
        logger.info("Saga terminée (lobby purgé): lobbyId={}", lobbyId);
        SagaLifecycle.end();
    }

    private void schedulePurge() {
        cancelDeadlines();
        this.purgeDeadlineId = deadlineManager.schedule(
                LobbyDeadline.LOBBY_RETENTION_DURATION,
                LobbyDeadline.LOBBY_PURGE
        );
    }

    private void cancelDeadlines() {
        cancelMatchmakingDeadline();
        cancelPurgeDeadline();
    }

    private void cancelMatchmakingDeadline() {
        if (matchmakingDeadlineId != null) {
            deadlineManager.cancelSchedule(LobbyDeadline.MATCHMAKING_DEADLINE, matchmakingDeadlineId);
            matchmakingDeadlineId = null;
        }
    }

    private void cancelPurgeDeadline() {
        if (purgeDeadlineId != null) {
            deadlineManager.cancelSchedule(LobbyDeadline.LOBBY_PURGE, purgeDeadlineId);
            purgeDeadlineId = null;
        }
    }

    private GamePlayerType toGamePlayerType(LobbyParticipantType lobbyParticipantType) {
        return switch (lobbyParticipantType) {
            case BOT -> GamePlayerType.BOT;
            case HUMAN -> GamePlayerType.HUMAN;
        };
    }
}
