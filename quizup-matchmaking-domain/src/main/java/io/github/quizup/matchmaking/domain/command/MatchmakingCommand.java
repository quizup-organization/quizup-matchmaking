package io.github.quizup.matchmaking.domain.command;

import io.github.quizup.matchmaking.domain.model.TopicLanguageCoverage;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.Set;

/**
 * Commandes de la recherche d'appariement public (duel « Défier le monde »).
 */
public interface MatchmakingCommand {

    String matchmakingId();

    /**
     * Démarre une recherche pour un sujet. {@code topicCoverage} est préparée par la façade
     * (query theme) : l'événement de démarrage est ainsi auto-suffisant et la saga n'interroge
     * jamais theme en bloquant.
     */
    record CreateMatchmakingCommand(
            @TargetAggregateIdentifier String matchmakingId,
            String playerId,
            String topicId,
            int level,
            Set<Language> languages,
            TopicLanguageCoverage topicCoverage
    ) implements MatchmakingCommand {
    }

    /** Annule une recherche (action du joueur). */
    record CancelMatchmakingCommand(
            @TargetAggregateIdentifier String matchmakingId,
            String playerId
    ) implements MatchmakingCommand {
    }

    /** Commande interne (saga) : la recherche a abouti (salle d'appariement créée). */
    record MarkMatchmakingMatchedCommand(
            @TargetAggregateIdentifier String matchmakingId,
            String opponentId,
            String roomId,
            boolean vsBot
    ) implements MatchmakingCommand {
    }

    /** Commande interne (saga) : la partie n'a pas pu être créée. */
    record FailMatchmakingCommand(
            @TargetAggregateIdentifier String matchmakingId,
            String reason
    ) implements MatchmakingCommand {
    }

    /** Commande interne (saga, après rétention) : purge un état terminal. */
    record PurgeMatchmakingCommand(
            @TargetAggregateIdentifier String matchmakingId
    ) implements MatchmakingCommand {
    }
}
