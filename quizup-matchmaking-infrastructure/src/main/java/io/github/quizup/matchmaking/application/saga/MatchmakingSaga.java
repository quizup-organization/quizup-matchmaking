package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.MatchmakingCandidate;
import io.github.quizup.matchmaking.domain.model.MatchmakingDeadline;
import io.github.quizup.matchmaking.domain.model.MatchmakingRules;
import io.github.quizup.matchmaking.domain.model.TopicLanguageCoverage;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPoolPort;
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
 * Le dernier arrivé initie le pairage : il cherche un candidat plus ancien, compatible (même
 * sujet, Elo ±{@code RATING_WINDOW}, langues couvertes par le sujet) puis crée la <b>salle</b>
 * (fire-and-forget, comme le défi nominatif) et marque les deux recherches comme appariées.
 * À l'échéance (**30 s**) sans adversaire, le ticket **s'arrête** ({@code EXPIRED}) — aucun repli
 * bot automatique : le client propose de relancer une recherche ou de basculer explicitement sur
 * un entraînement. Le matchmaking ne prépare
 * jamais la partie : la salle est l'unique point d'échec, annoncé aux joueurs présents par
 * {@code RoomSaga}. La couverture linguistique du sujet est portée par
 * {@link MatchmakingEvent.MatchmakingStartedEvent} (photo préparée par la façade) : la saga
 * compare localement, sans query bloquante sur le thread du processor. Les exceptions des ports
 * (pool) font échouer les tickets via {@link ExceptionHandler}.
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
    private int rating;

    @Getter
    @Setter
    private TopicLanguageCoverage topicCoverage;

    @Getter
    @Setter
    private Instant startedAt;

    @Getter
    @Setter
    private String deadlineId;

    @Getter
    @Setter
    private String purgeDeadlineId;

    @Getter
    @Setter
    private boolean finished;

    @StartSaga
    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingStartedEvent event) {
        this.matchmakingId = event.matchmakingId();
        this.playerId = event.playerId();
        this.topicId = event.topicId();
        this.rating = event.rating();
        this.topicCoverage = event.topicCoverage();
        this.startedAt = event.startedAt();

        this.deadlineId = deadlineManager.schedule(
                MatchmakingDeadline.MATCHMAKING_DEADLINE_DURATION,
                MatchmakingDeadline.MATCHMAKING_DEADLINE);

        tryMatch(event.languages());
    }

    /** Tente d'apparier avec un candidat plus ancien et compatible (couverture linguistique locale). */
    private void tryMatch(Set<Language> myLanguages) {
        List<MatchmakingCandidate> candidates =
                pool.candidates(topicId, rating, MatchmakingRules.RATING_WINDOW, playerId, CANDIDATE_LIMIT);

        for (MatchmakingCandidate candidate : candidates) {
            if (!candidate.createdAt().isBefore(startedAt)) {
                continue; // seul le plus récent initie
            }
            Set<Language> union = union(myLanguages, candidate.languages());
            if (topicCoverage == null
                    || topicCoverage.approvedCountFor(union) < GameRules.TOTAL_ROUNDS) {
                continue;
            }
            if (pool.claim(candidate.matchmakingId(), playerId).isEmpty()) {
                continue; // course : candidat déjà pris
            }
            createHumanRoom(candidate);
            return;
        }
    }

    private void createHumanRoom(MatchmakingCandidate candidate) {
        String roomId = UUID.randomUUID().toString();
        // Fire-and-forget : la salle est l'unique point d'échec (RoomFailedEvent).
        commandGateway.send(new RoomCommand.CreateRoomCommand(
                roomId, topicId, playerId, candidate.playerId(), false, null));
        commandGateway.send(new MatchmakingCommand.MarkMatchmakingMatchedCommand(
                matchmakingId, candidate.playerId(), roomId, false));
        commandGateway.send(new MatchmakingCommand.MarkMatchmakingMatchedCommand(
                candidate.matchmakingId(), playerId, roomId, false));
        this.finished = true;
    }

    @DeadlineHandler(deadlineName = MatchmakingDeadline.MATCHMAKING_DEADLINE)
    public void onDeadline() {
        if (finished) {
            return;
        }
        logger.info("Fenêtre de recherche écoulée sans adversaire: matchmakingId={}", matchmakingId);
        commandGateway.send(new MatchmakingCommand.ExpireMatchmakingCommand(matchmakingId));
        this.finished = true;
    }

    /**
     * Échec synchrone d'un handler de la saga (pool, vérification de couverture du sujet) :
     * le ticket passe en {@code FAILED} au lieu de bloquer le processor. Les échecs asynchrones
     * des commandes {@code send} ne passent pas ici — les créations d'agrégats sont pures.
     */
    @ExceptionHandler
    public void on(Exception exception) {
        logger.error("Échec de l'appariement: matchmakingId={}", matchmakingId, exception);
        commandGateway.send(new MatchmakingCommand.FailMatchmakingCommand(matchmakingId, "MATCHMAKING_ERROR"));
    }

    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingMatchedEvent event) {
        cancelDeadline();
        schedulePurge();
        this.finished = true;
    }

    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingCancelledEvent event) {
        cancelDeadline();
        schedulePurge();
        this.finished = true;
    }

    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingExpiredEvent event) {
        cancelDeadline();
        schedulePurge();
        this.finished = true;
    }

    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingFailedEvent event) {
        cancelDeadline();
        schedulePurge();
        this.finished = true;
    }

    @DeadlineHandler(deadlineName = MatchmakingDeadline.MATCHMAKING_PURGE)
    public void onPurge() {
        commandGateway.send(new MatchmakingCommand.PurgeMatchmakingCommand(matchmakingId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "matchmakingId")
    public void on(MatchmakingEvent.MatchmakingPurgedEvent event) {
        cancelDeadlines();
        logger.info("Saga matchmaking terminée (purgée): matchmakingId={}", matchmakingId);
    }

    private void schedulePurge() {
        if (purgeDeadlineId == null) {
            purgeDeadlineId = deadlineManager.schedule(
                    MatchmakingDeadline.MATCHMAKING_RETENTION_DURATION,
                    MatchmakingDeadline.MATCHMAKING_PURGE);
        }
    }

    private void cancelDeadlines() {
        cancelDeadline();
        if (purgeDeadlineId != null) {
            deadlineManager.cancelSchedule(MatchmakingDeadline.MATCHMAKING_PURGE, purgeDeadlineId);
            purgeDeadlineId = null;
        }
    }

    private void cancelDeadline() {
        if (deadlineId != null) {
            deadlineManager.cancelSchedule(MatchmakingDeadline.MATCHMAKING_DEADLINE, deadlineId);
            deadlineId = null;
        }
    }

    private static Set<Language> union(Set<Language> left, Set<Language> right) {
        Set<Language> union = new HashSet<>();
        if (left != null) union.addAll(left);
        if (right != null) union.addAll(right);
        return union;
    }
}
