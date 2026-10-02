package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.MatchmakingCandidate;
import io.github.quizup.matchmaking.domain.model.MatchmakingRules;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPoolPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de l'appariement public (« Défier le monde »).
 * <p>
 * Le dernier arrivé initie le pairage : il cherche un candidat plus ancien, compatible
 * (même sujet, niveau ±{@code LEVEL_WINDOW}, langues couvertes par le sujet), le réclame
 * atomiquement, crée la partie puis marque les deux recherches comme appariées.
 * À l'échéance (5 s) sans adversaire, il crée une partie bot.
 */
@Saga
@ProcessingGroup("matchmaking-saga")
public class MatchmakingSaga {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingSaga.class);
    private static final int CANDIDATE_LIMIT = 10;

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Autowired
    private transient MatchmakingPoolPort pool;

    @Autowired
    private transient MatchmakingPlayerPort playerPort;

    @Autowired
    private transient TopicAvailabilityPort topicAvailabilityPort;

    @Getter
    @Setter
    private String matchmakingId;

    @Getter
    @Setter
    private String playerId;

    @Getter
    @Setter
    private String topicId;

    @Getter
    @Setter
    private int level;

    @Getter
    @Setter
    private Set<Language> languages;

    @Getter
    @Setter
    private Instant startedAt;

    @Getter
    @Setter
    private String deadlineId;

    @Getter
    @Setter
    private boolean finished;

    @StartSaga
    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingStartedEvent event) {
        this.matchmakingId = event.matchmakingId();
        this.playerId = event.playerId();
        this.topicId = event.topicId();
        this.level = event.level();
        this.startedAt = event.startedAt();
        this.languages = event.languages();

        this.deadlineId = deadlineManager.schedule(
                LobbyDeadline.MATCHMAKING_DEADLINE_DURATION,
                LobbyDeadline.MATCHMAKING_DEADLINE);

        tryMatch(event.languages());
    }

    /** Tente d'apparier avec un candidat plus ancien et compatible. */
    private void tryMatch(Set<Language> myLanguages) {
        List<MatchmakingCandidate> candidates =
                pool.candidates(topicId, level, MatchmakingRules.LEVEL_WINDOW, playerId, CANDIDATE_LIMIT);

        for (MatchmakingCandidate candidate : candidates) {
            if (!candidate.createdAt().isBefore(startedAt)) {
                continue; // seul le plus récent initie
            }
            Set<Language> union = union(myLanguages, candidate.languages());
            if (!topicAvailabilityPort.coversAllLanguages(topicId, union)) {
                continue;
            }
            if (pool.claim(candidate.matchmakingId(), playerId).isEmpty()) {
                continue; // course : candidat déjà pris
            }
            createHumanGame(candidate, union);
            return;
        }
    }

    private void createHumanGame(MatchmakingCandidate candidate, Set<Language> union) {
        String gameId = UUID.randomUUID().toString();
        PlayerSummary me = playerPort.getPlayer(playerId);
        PlayerSummary opponent = playerPort.getPlayer(candidate.playerId());
        try {
            commandGateway.send(new GameCommand.CreateGameCommand(
                    gameId,
                    topicId,
                    playerId,
                    nameOf(me),
                    candidate.playerId(),
                    nameOf(opponent),
                    union,
                    GamePlayerType.HUMAN,
                    null));
            commandGateway.send(new MatchmakingCommand.MarkMatchmakingMatchedCommand(
                    matchmakingId, candidate.playerId(), gameId, false));
            commandGateway.send(new MatchmakingCommand.MarkMatchmakingMatchedCommand(
                    candidate.matchmakingId(), playerId, gameId, false));
            this.finished = true;
        } catch (Exception exception) {
            logger.error("Échec de création de partie pour l'appariement {} / {}", matchmakingId, candidate.matchmakingId(), exception);
            commandGateway.send(new MatchmakingCommand.FailMatchmakingCommand(matchmakingId, "GAME_CREATION_FAILED"));
            commandGateway.send(new MatchmakingCommand.FailMatchmakingCommand(candidate.matchmakingId(), "GAME_CREATION_FAILED"));
            this.finished = true;
        }
    }

    @DeadlineHandler(deadlineName = LobbyDeadline.MATCHMAKING_DEADLINE)
    public void onDeadline() {
        if (finished) {
            return;
        }
        logger.info("Timeout matchmaking — partie bot: matchmakingId={}", matchmakingId);
        String gameId = UUID.randomUUID().toString();
        PlayerSummary me = playerPort.getPlayer(playerId);
        try {
            commandGateway.send(new GameCommand.CreateGameCommand(
                    gameId,
                    topicId,
                    playerId,
                    nameOf(me),
                    QuizUpConstants.SYSTEM_USER_ID,
                    QuizUpConstants.SYSTEM_USER_NAME,
                    languages == null ? Set.of() : Set.copyOf(languages),
                    GamePlayerType.BOT,
                    null));
            commandGateway.send(new MatchmakingCommand.MarkMatchmakingMatchedCommand(
                    matchmakingId, null, gameId, true));
        } catch (Exception exception) {
            logger.error("Échec de création de partie bot: matchmakingId={}", matchmakingId, exception);
            commandGateway.send(new MatchmakingCommand.FailMatchmakingCommand(matchmakingId, "GAME_CREATION_FAILED"));
        }
        this.finished = true;
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingMatchedEvent event) {
        cancelDeadline();
        this.finished = true;
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingCancelledEvent event) {
        cancelDeadline();
        this.finished = true;
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingFailedEvent event) {
        cancelDeadline();
        this.finished = true;
    }

    private void cancelDeadline() {
        if (deadlineId != null) {
            deadlineManager.cancelSchedule(LobbyDeadline.MATCHMAKING_DEADLINE, deadlineId);
            deadlineId = null;
        }
    }

    private static Set<Language> union(Set<Language> left, Set<Language> right) {
        Set<Language> union = new HashSet<>();
        if (left != null) union.addAll(left);
        if (right != null) union.addAll(right);
        return union;
    }

    private static String nameOf(PlayerSummary player) {
        return player == null || player.pseudonym() == null ? "" : player.pseudonym();
    }
}
