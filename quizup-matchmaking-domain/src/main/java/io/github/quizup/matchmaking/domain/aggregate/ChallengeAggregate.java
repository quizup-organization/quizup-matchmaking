package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.model.ChallengeDeadline;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * ChallengeAggregate — défi nominatif : intention asynchrone « A défie B » sur un sujet.
 * <p>
 * Ne porte **ni présence ni temps réel** : l'acceptation crée une salle (saga) qui ouvre le
 * cycle de vie temps réel (présence, ready check, partie). Le défi se contente de la réponse
 * (accepté/refusé/annulé/expiré) et conserve le lien vers la salle.
 */
@Aggregate
public class ChallengeAggregate {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeAggregate.class);

    @AggregateIdentifier
    private String challengeId;
    private String topicId;
    private String challengerId;
    private String opponentId;
    private String roomId;
    private ChallengeStatus status;

    protected ChallengeAggregate() {
    }

    /** Ouvre un défi nominatif ; le thème doit couvrir les langues des deux joueurs. */
    @CommandHandler
    public ChallengeAggregate(ChallengeCommand.CreateChallengeCommand command,
                              ProfileRepositoryPort profileRepositoryPort,
                              TopicAvailabilityPort topicAvailabilityPort) {
        if (StringUtils.isBlank(command.challengerId())) {
            throw new ChallengeExceptions.MissingChallengerIdentifierProblem(command.challengeId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new ChallengeExceptions.MissingTopicIdentifierProblem(command.challengeId());
        }
        if (StringUtils.isBlank(command.opponentId())) {
            throw new ChallengeExceptions.MissingOpponentIdentifierProblem(command.challengeId());
        }
        if (command.opponentId().equals(command.challengerId())) {
            throw new ChallengeExceptions.CannotChallengeSelfProblem(command.challengeId(), command.challengerId());
        }
        requireTopicCoversLanguages(profileRepositoryPort, topicAvailabilityPort,
                command.challengerId(), command.opponentId(), command.topicId(), command.challengeId());

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
        apply(new ChallengeEvent.ChallengeAcceptedEvent(challengeId, challengerId, opponentId, Instant.now()));
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

    /** Commande interne (saga) : relie la salle créée à l'acceptation (idempotent). */
    @CommandHandler
    public void handle(ChallengeCommand.LinkChallengeRoomCommand command) {
        if (StringUtils.isNotBlank(roomId)) {
            return;
        }
        if (status != ChallengeStatus.ACCEPTED) {
            throw new ChallengeExceptions.ChallengeNotAcceptedProblem(challengeId, status.name());
        }
        apply(new ChallengeEvent.ChallengeRoomCreatedEvent(challengeId, command.roomId(), Instant.now()));
    }

    // ---------------------------------------------------------------------

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

    private static void requireTopicCoversLanguages(ProfileRepositoryPort profileRepositoryPort,
                                                    TopicAvailabilityPort topicAvailabilityPort,
                                                    String challengerId,
                                                    String opponentId,
                                                    String topicId,
                                                    String challengeId) {
        Set<Language> languages = new HashSet<>();
        LobbyPlayer challenger = profileRepositoryPort.getById(challengerId);
        LobbyPlayer opponent = profileRepositoryPort.getById(opponentId);
        if (challenger != null && challenger.language() != null) {
            languages.add(challenger.language());
        }
        if (opponent != null && opponent.language() != null) {
            languages.add(opponent.language());
        }
        if (!languages.isEmpty() && !topicAvailabilityPort.coversAllLanguages(topicId, languages)) {
            throw new ChallengeExceptions.TopicNotAvailableInLanguageProblem(challengeId, topicId, languages);
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
    public void on(ChallengeEvent.ChallengeRoomCreatedEvent event) {
        this.roomId = event.roomId();
    }
}
