package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines du défi nominatif (intention asynchrone). La salle temps réel a ses propres
 * deadlines ({@link LobbyDeadline}).
 */
public interface ChallengeDeadline {

    /** Expiration d'un défi jamais accepté. */
    String CHALLENGE_EXPIRY = "challenge-expiry";

    Duration CHALLENGE_EXPIRY_DURATION = Duration.ofHours(1);
}
