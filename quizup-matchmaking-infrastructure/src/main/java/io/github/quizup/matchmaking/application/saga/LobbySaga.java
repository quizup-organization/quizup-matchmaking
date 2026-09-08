package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GameMode;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.LobbyPolicy;
import io.github.quizup.matchmaking.domain.port.out.UserPort;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
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
 * 1. LobbyOpenedEvent → planifie WAITING_HUMAN_TIMEOUT
 * 2a. LobbyJoinedEvent → annule deadline, crée partie, ferme lobby
 * 2b. Deadline expire → LobbyPolicy décide → JoinLobbyCommand BOT
 * 3. LobbyClosedEvent → saga terminée
 * 4. LobbyCancelledEvent → saga terminée
 */
@Saga
public class LobbySaga {

    private static final Logger logger = LoggerFactory.getLogger(LobbySaga.class);

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Autowired
    private transient UserPort userPort;

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

    @StartSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyOpenedEvent event) {
        this.lobbyId = event.lobbyId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();

        deadlineManager.schedule(LobbyDeadline.MATCHMAKING_DEADLINE_DURATION, LobbyDeadline.MATCHMAKING_DEADLINE);

        logger.info("Lobby ouvert, attente humain {}s: lobbyId={}", LobbyDeadline.MATCHMAKING_DEADLINE_DURATION.getSeconds(), lobbyId);
    }

    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.challengerId = event.challengerId();

        cancelDeadlines();

        String gameId = UUID.randomUUID().toString();

        GamePlayerType gamePlayerType = toGamePlayerType(event.challengerType());

        commandGateway.send(new GameCommand.CreateGameCommand(
                gameId,
                topicId,
                initiatorId,
                userPort.findNameById(initiatorId),
                challengerId,
                toDisplayName(gamePlayerType, challengerId),
                GameMode.SYNC,
                gamePlayerType
        ));

        commandGateway.send(new LobbyCommand.CompleteLobbyCommand(lobbyId, gameId));

        logger.info("Partie orchestrée: lobbyId={}, gameId={}, initiator={}, challenger={}", lobbyId, gameId, initiatorId, challengerId);
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.MATCHMAKING_DEADLINE)
    public void onMatchmakingTimeout() {
        if (LobbyPolicy.shouldFallbackToBot(challengerId)) {
            logger.info("Timeout matchmaking — injection bot: lobbyId={}", lobbyId);
            commandGateway.send(new LobbyCommand.JoinLobbyCommand(lobbyId, LobbyPolicy.BOT_PLAYER_ID, LobbyParticipantType.BOT));
        }
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        cancelDeadlines();
        logger.info("Saga terminée (lobby fermé): lobbyId={}, gameId={}", lobbyId, event.gameId());
        SagaLifecycle.end();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "lobbyId")
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        cancelDeadlines();
        logger.info("Saga terminée (lobby annulé): lobbyId={}", lobbyId);
        SagaLifecycle.end();
    }

    private void cancelDeadlines() {
        try {
            deadlineManager.cancelAllWithinScope(LobbyDeadline.MATCHMAKING_DEADLINE);
        } catch (Exception ignored) {
        }
    }

    private String toDisplayName(GamePlayerType gamePlayerType, String userId) {
        return GamePlayerType.BOT.equals(gamePlayerType)
                ? QuizUpConstants.BOT_USER_NAME
                : userPort.findNameById(userId);
    }

    private GamePlayerType toGamePlayerType(LobbyParticipantType lobbyParticipantType) {
        return switch (lobbyParticipantType) {
            case BOT -> GamePlayerType.BOT;
            case HUMAN -> GamePlayerType.HUMAN;
        };
    }
}
