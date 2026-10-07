package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * MatchmakingAggregate — recherche d'un adversaire pour un duel public (« Défier le monde »).
 * <p>
 * N'a aucune notion de salon : c'est une file d'appariement. La saga associe deux recherches
 * compatibles (même sujet, niveau ±5, langues couvertes par le sujet) ou bascule sur une partie
 * bot à l'échéance.
 */
@Aggregate
public class MatchmakingAggregate {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingAggregate.class);

    @AggregateIdentifier
    private String matchmakingId;
    private String playerId;
    private String topicId;
    private int level;
    private Set<Language> languages;
    private MatchmakingStatus status;
    private String opponentId;
    private String gameId;
    private boolean vsBot;

    protected MatchmakingAggregate() {
    }

    @CommandHandler
    public MatchmakingAggregate(MatchmakingCommand.CreateMatchmakingCommand command) {
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.matchmakingId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new LobbyExceptions.MissingTopicIdentifierProblem(command.matchmakingId());
        }
        logger.info("Starting matchmaking: matchmakingId={}, playerId={}, topicId={}, level={}",
                command.matchmakingId(), command.playerId(), command.topicId(), command.level());
        apply(new MatchmakingEvent.MatchmakingStartedEvent(
                command.matchmakingId(),
                command.playerId(),
                command.topicId(),
                command.level(),
                command.languages(),
                Instant.now()));
    }

    @CommandHandler
    public void handle(MatchmakingCommand.CancelMatchmakingCommand command) {
        if (status != MatchmakingStatus.SEARCHING) {
            return;
        }
        if (!command.playerId().equals(playerId)) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(matchmakingId, command.playerId());
        }
        apply(new MatchmakingEvent.MatchmakingCancelledEvent(matchmakingId, "PLAYER_CANCELLED", Instant.now()));
    }

    @CommandHandler
    public void handle(MatchmakingCommand.MarkMatchmakingMatchedCommand command) {
        if (status == MatchmakingStatus.CLOSED) {
            if (command.gameId().equals(gameId)) {
                return;
            }
            throw new LobbyExceptions.MatchmakingNotSearchingProblem(matchmakingId, status.name());
        }
        if (status != MatchmakingStatus.SEARCHING) {
            throw new LobbyExceptions.MatchmakingNotSearchingProblem(matchmakingId, status.name());
        }
        logger.info("Matching: matchmakingId={}, opponentId={}, gameId={}, vsBot={}",
                matchmakingId, command.opponentId(), command.gameId(), command.vsBot());
        apply(new MatchmakingEvent.MatchmakingMatchedEvent(
                matchmakingId,
                command.opponentId(),
                command.gameId(),
                command.vsBot(),
                Instant.now()));
    }

    /** Commande interne (saga, après rétention) : purge l'état terminal, l'agrégat est supprimé. */
    @CommandHandler
    public void handle(MatchmakingCommand.PurgeMatchmakingCommand command) {
        logger.info("Purging matchmaking: matchmakingId={}", matchmakingId);
        apply(new MatchmakingEvent.MatchmakingPurgedEvent(matchmakingId, Instant.now()));
    }

    @CommandHandler
    public void handle(MatchmakingCommand.FailMatchmakingCommand command) {
        if (status != MatchmakingStatus.SEARCHING) {
            return;
        }
        logger.warn("Matchmaking failed: matchmakingId={}, reason={}", matchmakingId, command.reason());
        apply(new MatchmakingEvent.MatchmakingFailedEvent(matchmakingId, command.reason(), Instant.now()));
    }

    // ============================ Event Sourcing ============================

    @EventSourcingHandler
    public void on(MatchmakingEvent.MatchmakingStartedEvent event) {
        this.matchmakingId = event.matchmakingId();
        this.playerId = event.playerId();
        this.topicId = event.topicId();
        this.level = event.level();
        this.languages = event.languages();
        this.status = MatchmakingStatus.SEARCHING;
    }

    @EventSourcingHandler
    public void on(MatchmakingEvent.MatchmakingMatchedEvent event) {
        this.status = MatchmakingStatus.CLOSED;
        this.opponentId = event.opponentId();
        this.gameId = event.gameId();
        this.vsBot = event.vsBot();
    }

    @EventSourcingHandler
    public void on(MatchmakingEvent.MatchmakingCancelledEvent event) {
        this.status = MatchmakingStatus.CLOSED;
    }

    @EventSourcingHandler
    public void on(MatchmakingEvent.MatchmakingFailedEvent event) {
        this.status = MatchmakingStatus.FAILED;
    }

    @EventSourcingHandler
    public void on(MatchmakingEvent.MatchmakingPurgedEvent event) {
        AggregateLifecycle.markDeleted();
    }
}
