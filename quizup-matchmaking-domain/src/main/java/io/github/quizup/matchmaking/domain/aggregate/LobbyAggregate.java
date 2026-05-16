package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
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

@Aggregate
public class LobbyAggregate {

    private static final Logger logger = LoggerFactory.getLogger(LobbyAggregate.class);

    @AggregateIdentifier
    private String lobbyId;
    private String topicId;
    private String initiatorId;
    private String challengerId;
    private LobbyParticipantType challengerType;
    private boolean completed;
    private boolean cancelled;

    protected LobbyAggregate() {
    }

    @CommandHandler
    public LobbyAggregate(LobbyCommand.OpenLobbyCommand command) {
        if (StringUtils.isBlank(command.initiatorId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new LobbyExceptions.MissingTopicIdentifierProblem(command.lobbyId());
        }
        logger.info("Opening lobby: lobbyId={}, initiatorId={}, topicId={}",
                command.lobbyId(), command.initiatorId(), command.topicId());
        apply(new LobbyEvent.LobbyOpenedEvent(
                command.lobbyId(), command.topicId(), command.initiatorId(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.JoinLobbyCommand command) {
        if (completed || cancelled) {
            throw new LobbyExceptions.LobbyNotAvailableProblem(lobbyId);
        }
        if (StringUtils.isBlank(command.challengerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(lobbyId);
        }
        if (command.challengerId().equals(initiatorId)) {
            throw new LobbyExceptions.PlayerAlreadyInLobbyProblem(lobbyId, command.challengerId());
        }
        if (StringUtils.isNotBlank(challengerId)) {
            throw new LobbyExceptions.LobbyAlreadyFullProblem(lobbyId);
        }
        logger.info("Joining lobby: lobbyId={}, challengerId={}, type={}",
                lobbyId, command.challengerId(), command.challengerType());
        apply(new LobbyEvent.LobbyJoinedEvent(
                lobbyId, command.challengerId(), command.challengerType(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.CancelLobbyCommand command) {
        if (completed) {
            throw new LobbyExceptions.LobbyAlreadyClosedProblem(lobbyId);
        }
        if (cancelled) {
            throw new LobbyExceptions.LobbyAlreadyCancelledProblem(lobbyId);
        }
        if (!command.initiatorId().equals(initiatorId)) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(lobbyId, command.initiatorId());
        }
        logger.info("Cancelling lobby: lobbyId={}", lobbyId);
        apply(new LobbyEvent.LobbyCancelledEvent(lobbyId, command.initiatorId(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.CompleteLobbyCommand command) {
        if (StringUtils.isBlank(command.gameId())) {
            throw new LobbyExceptions.MissingGameIdentifierProblem(lobbyId);
        }
        if (StringUtils.isBlank(challengerId)) {
            throw new LobbyExceptions.ChallengerNotPresentProblem(lobbyId);
        }

        logger.info("Closing lobby: lobbyId={}, gameId={}", lobbyId, command.gameId());

        apply(
                new LobbyEvent.LobbyCompletedEvent(
                        lobbyId,
                        command.gameId(),
                        initiatorId,
                        challengerId,
                        topicId,
                        LobbyParticipantType.BOT.equals(challengerType),
                        Instant.now()
                )
        );
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyOpenedEvent event) {
        this.lobbyId = event.lobbyId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.challengerId = event.challengerId();
        this.challengerType = event.challengerType();
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        this.cancelled = true;
        AggregateLifecycle.markDeleted();
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        this.completed = true;
        AggregateLifecycle.markDeleted();
    }
}
