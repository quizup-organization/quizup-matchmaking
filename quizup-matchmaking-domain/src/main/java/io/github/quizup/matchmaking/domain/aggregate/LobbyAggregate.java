package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
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
 * LobbyAggregate — salon privé : salle d'attente entre deux humains.
 * <p>
 * Le créateur est le premier participant ; le second rejoint via {@code JoinLobbyCommand}.
 * Quand la partie est créée, l'agrégat est purgé immédiatement (aucun statut persistant).
 * Aucune notion de bot ni d'appariement ici.
 */
@Aggregate
public class LobbyAggregate {

    private static final Logger logger = LoggerFactory.getLogger(LobbyAggregate.class);

    @AggregateIdentifier
    private String lobbyId;
    private String topicId;
    private String initiatorId;
    private String participantId;
    private LobbyStatus status;
    private boolean completed;

    protected LobbyAggregate() {
    }

    @CommandHandler
    public LobbyAggregate(LobbyCommand.CreateLobbyCommand command) {
        if (StringUtils.isBlank(command.initiatorId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new LobbyExceptions.MissingTopicIdentifierProblem(command.lobbyId());
        }
        logger.info("Creating lobby: lobbyId={}, topicId={}, initiatorId={}",
                command.lobbyId(), command.topicId(), command.initiatorId());
        apply(new LobbyEvent.LobbyCreatedEvent(
                command.lobbyId(),
                command.topicId(),
                command.initiatorId(),
                Instant.now().plus(io.github.quizup.matchmaking.domain.model.LobbyDeadline.LOBBY_EXPIRY_DURATION),
                Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.JoinLobbyCommand command) {
        if (isClosed()) {
            throw new LobbyExceptions.LobbyNotAvailableProblem(lobbyId);
        }
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(lobbyId);
        }
        if (command.playerId().equals(initiatorId) || command.playerId().equals(participantId)) {
            // Idempotent : rejoindre à nouveau son propre salon ne fait rien.
            return;
        }
        if (StringUtils.isNotBlank(participantId)) {
            throw new LobbyExceptions.LobbyAlreadyFullProblem(lobbyId);
        }
        logger.info("Joining lobby: lobbyId={}, participantId={}", lobbyId, command.playerId());
        apply(new LobbyEvent.LobbyJoinedEvent(lobbyId, command.playerId(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.LeaveLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        requireParticipant(command.playerId());
        Instant now = Instant.now();
        apply(new LobbyEvent.LobbyLeftEvent(lobbyId, command.playerId(), now));
        apply(new LobbyEvent.LobbyCancelledEvent(lobbyId, initiatorId, "PLAYER_LEFT", now));
    }

    @CommandHandler
    public void handle(LobbyCommand.CancelLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        if (!command.playerId().equals(initiatorId)) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(lobbyId, command.playerId());
        }
        apply(new LobbyEvent.LobbyCancelledEvent(lobbyId, initiatorId, "PLAYER_CANCELLED", Instant.now()));
    }

    /** Succès : la partie est créée, le salon est purgé immédiatement. */
    @CommandHandler
    public void handle(LobbyCommand.CompleteLobbyCommand command) {
        if (StringUtils.isBlank(command.gameId())) {
            throw new LobbyExceptions.MissingGameIdentifierProblem(lobbyId);
        }
        if (StringUtils.isBlank(participantId)) {
            throw new LobbyExceptions.ParticipantNotPresentProblem(lobbyId);
        }
        logger.info("Completing lobby: lobbyId={}, gameId={}", lobbyId, command.gameId());
        apply(new LobbyEvent.LobbyCompletedEvent(lobbyId, command.gameId(), Instant.now()));
        apply(new LobbyEvent.LobbyPurgedEvent(lobbyId, Instant.now()));
    }

    /** Échec système : la partie n'a pas pu être créée. */
    @CommandHandler
    public void handle(LobbyCommand.FailLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        logger.warn("Failing lobby: lobbyId={}, reason={}", lobbyId, command.reason());
        apply(new LobbyEvent.LobbyFailedEvent(lobbyId, command.reason(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.ExpireLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        apply(new LobbyEvent.LobbyExpiredEvent(lobbyId, Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.PurgeLobbyCommand command) {
        logger.info("Purging lobby: lobbyId={}", lobbyId);
        apply(new LobbyEvent.LobbyPurgedEvent(lobbyId, Instant.now()));
    }

    // ---------------------------------------------------------------------

    private void requireParticipant(String playerId) {
        if (!playerId.equals(initiatorId) && !playerId.equals(participantId)) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(lobbyId, playerId);
        }
    }

    private boolean isClosed() {
        return completed || status == LobbyStatus.CANCELLED || status == LobbyStatus.EXPIRED || status == LobbyStatus.FAILED;
    }

    // ============================ Event Sourcing ============================

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCreatedEvent event) {
        this.lobbyId = event.lobbyId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
        this.status = LobbyStatus.OPEN;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.participantId = event.participantId();
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        this.completed = true;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyLeftEvent event) {
        if (event.playerId().equals(participantId)) {
            this.participantId = null;
        }
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        this.status = LobbyStatus.CANCELLED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyFailedEvent event) {
        this.status = LobbyStatus.FAILED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyExpiredEvent event) {
        this.status = LobbyStatus.EXPIRED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        AggregateLifecycle.markDeleted();
    }
}
