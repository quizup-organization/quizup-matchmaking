package io.github.quizup.matchmaking.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Commandes du défi nominatif (intention asynchrone).
 */
public interface ChallengeCommand {

    String challengeId();

    /** A défie B sur un sujet ; la salle n'est créée qu'à l'acceptation. */
    record CreateChallengeCommand(
            @TargetAggregateIdentifier String challengeId,
            String topicId,
            String challengerId,
            String opponentId
    ) implements ChallengeCommand {
    }

    /** B accepte : la salle temps réel est créée par la saga. */
    record AcceptChallengeCommand(
            @TargetAggregateIdentifier String challengeId,
            String playerId
    ) implements ChallengeCommand {
    }

    /** B refuse. */
    record DeclineChallengeCommand(
            @TargetAggregateIdentifier String challengeId,
            String playerId
    ) implements ChallengeCommand {
    }

    /** A annule son défi resté sans réponse. */
    record CancelChallengeCommand(
            @TargetAggregateIdentifier String challengeId,
            String playerId
    ) implements ChallengeCommand {
    }

    /** Commande interne (saga, deadline) : défi jamais accepté, expiré. */
    record ExpireChallengeCommand(
            @TargetAggregateIdentifier String challengeId
    ) implements ChallengeCommand {
    }

    /** Commande interne (saga) : relie la salle créée à l'acceptation (idempotent). */
    record LinkChallengeRoomCommand(
            @TargetAggregateIdentifier String challengeId,
            String roomId
    ) implements ChallengeCommand {
    }
}
