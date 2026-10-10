package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.model.ChallengeDeadline;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * ChallengeAggregate — défi nominatif : intention asynchrone « A défie B » sur un sujet.
 * <p>
 * Ne porte **ni présence ni temps réel**, et ne valide **pas la faisabilité** du duel
 * (couverture linguistique, questions disponibles) : ce travail appartient à la salle, qui
 * prépare la partie et annonce l'échec aux joueurs présents. L'agrégat se limite aux invariants
 * d'intégrité de la commande (identifiants, pas d'auto-défi, statut) et au lien dérivé vers la
 * salle ({@code ChallengeRoomId}).
 */
@Aggregate
public class ChallengeAggregate {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeAggregate.class);

    @AggregateIdentifier
    private String challengeId;
    private String topicId;
    private String challengerId;
    private String opponentId;
    private ChallengeStatus status;

    protected ChallengeAggregate() {
    }

    /** Ouvre un défi nominatif ; la faisabilité du duel sera vérifiée par la salle. */
    @CommandHandler
    public ChallengeAggregate(ChallengeCommand.CreateChallengeCommand command) {
        requireChallengerId(command.challengerId(), command.challengeId());
        requireTopicId(command.topicId(), command.challengeId());
        requireOpponentId(command.opponentId(), command.challengeId());
        requireOpponentNotSelf(command.opponentId(), command.challengerId(), command.challengeId());

        logger.info("Creating challenge: challengeId={}, topicId={}, challengerId={}, opponentId={}",
                command.challengeId(), command.topicId(), command.challengerId(), command.opponentId());
        apply(new ChallengeEvent.ChallengeCreatedEvent(
                command.challengeId(),
                command.topicId(),
                command.challengerId(),
                command.opponentId(),
                Instant.now().plus(ChallengeDeadline.CHALLENGE_EXPIRY_DURATION),
                Instant.now()));
    }

    @CommandHandler
    public void handle(ChallengeCommand.AcceptChallengeCommand command) {
        requireOpponent(command.playerId());
        if (status == ChallengeStatus.ACCEPTED) {
            // Idempotent : une relecture/duplication de l'acceptation ne fait rien.
            return;
        }
        requirePending();
        logger.info("Accepting challenge: challengeId={}, opponentId={}", challengeId, command.playerId());
        apply(new ChallengeEvent.ChallengeAcceptedEvent(
                challengeId, topicId, challengerId, opponentId, Instant.now()));
    }

    @CommandHandler
    public void handle(ChallengeCommand.DeclineChallengeCommand command) {
        requireOpponent(command.playerId());
        requirePending();
        logger.info("Declining challenge: challengeId={}, opponentId={}", challengeId, command.playerId());
        apply(new ChallengeEvent.ChallengeDeclinedEvent(challengeId, challengerId, opponentId, Instant.now()));
    }

    @CommandHandler
    public void handle(ChallengeCommand.CancelChallengeCommand command) {
        requirePending();
        if (!command.playerId().equals(challengerId)) {
            throw new ChallengeExceptions.PlayerNotChallengerProblem(challengeId, command.playerId());
        }
        apply(new ChallengeEvent.ChallengeCancelledEvent(challengeId, challengerId, Instant.now()));
    }

    @CommandHandler
    public void handle(ChallengeCommand.ExpireChallengeCommand command) {
        if (status != ChallengeStatus.PENDING) {
            // Deadline tardive : le défi a déjà été résolu (accepté/refusé/annulé).
            return;
        }
        apply(new ChallengeEvent.ChallengeExpiredEvent(challengeId, Instant.now()));
    }

    /** Commande interne (saga, après rétention) : purge l'état terminal, l'agrégat est supprimé. */
    @CommandHandler
    public void handle(ChallengeCommand.PurgeChallengeCommand command) {
        logger.info("Purging challenge: challengeId={}", challengeId);
        apply(new ChallengeEvent.ChallengePurgedEvent(challengeId, Instant.now()));
    }

    // ---------------------------------------------------------------------

    // ── Validateurs (requireXxx : nommés, appelés en tête de handler) ──

    private static void requireChallengerId(String challengerId, String challengeId) {
        if (StringUtils.isBlank(challengerId)) {
            throw new ChallengeExceptions.MissingChallengerIdentifierProblem(challengeId);
        }
    }

    private static void requireTopicId(String topicId, String challengeId) {
        if (StringUtils.isBlank(topicId)) {
            throw new ChallengeExceptions.MissingTopicIdentifierProblem(challengeId);
        }
    }

    private static void requireOpponentId(String opponentId, String challengeId) {
        if (StringUtils.isBlank(opponentId)) {
            throw new ChallengeExceptions.MissingOpponentIdentifierProblem(challengeId);
        }
    }

    private static void requireOpponentNotSelf(String opponentId, String challengerId, String challengeId) {
        if (opponentId.equals(challengerId)) {
            throw new ChallengeExceptions.CannotChallengeSelfProblem(challengeId, challengerId);
        }
    }

    private void requirePending() {
        if (status != ChallengeStatus.PENDING) {
            throw new ChallengeExceptions.ChallengeNotPendingProblem(challengeId, status.name());
        }
    }

    private void requireOpponent(String playerId) {
        if (StringUtils.isBlank(playerId) || !playerId.equals(opponentId)) {
            throw new ChallengeExceptions.ChallengeNotInvitedProblem(challengeId, playerId);
        }
    }

    // ============================ Event Sourcing ============================

    @EventSourcingHandler
    public void on(ChallengeEvent.ChallengeCreatedEvent event) {
        this.challengeId = event.challengeId();
        this.topicId = event.topicId();
        this.challengerId = event.challengerId();
        this.opponentId = event.opponentId();
        this.status = ChallengeStatus.PENDING;
    }

    @EventSourcingHandler
    public void on(ChallengeEvent.ChallengeAcceptedEvent event) {
        this.status = ChallengeStatus.ACCEPTED;
    }

    @EventSourcingHandler
    public void on(ChallengeEvent.ChallengeDeclinedEvent event) {
        this.status = ChallengeStatus.DECLINED;
    }

    @EventSourcingHandler
    public void on(ChallengeEvent.ChallengeCancelledEvent event) {
        this.status = ChallengeStatus.CANCELLED;
    }

    @EventSourcingHandler
    public void on(ChallengeEvent.ChallengeExpiredEvent event) {
        this.status = ChallengeStatus.EXPIRED;
    }

    @EventSourcingHandler
    public void on(ChallengeEvent.ChallengePurgedEvent event) {
        AggregateLifecycle.markDeleted();
    }
}
