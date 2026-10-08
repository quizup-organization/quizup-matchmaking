package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.Challenge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port sortant du read model des défis nominatifs. */
public interface ChallengeRepositoryPort {

    void save(Challenge challenge);

    Optional<Challenge> findById(String challengeId);

    /** Défis encore en attente de réponse où le joueur est lanceur ou invité. */
    List<Challenge> findPendingByPlayerId(String playerId);

    /**
     * Identifiants des défis terminaux ({@code status != PENDING}) résolus avant {@code before},
     * filet de sécurité du balayeur de purge (défis antérieurs à la saga de purge, deadlines
     * perdues).
     */
    List<String> findTerminalIdsBefore(Instant before);

    /** Supprime le read model d'un défi purgé (après rétention). */
    void deleteById(String challengeId);
}
