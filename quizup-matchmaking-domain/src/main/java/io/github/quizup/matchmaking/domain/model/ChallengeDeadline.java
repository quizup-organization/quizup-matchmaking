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

    /** Rétention d'un défi terminal avant purge : le client lit encore la réponse. */
    String CHALLENGE_PURGE = "challenge-purge";

    Duration CHALLENGE_RETENTION_DURATION = Duration.ofMinutes(2);

    /**
     * Marge avant que le balayeur de purge ({@code ChallengePurgeSweeper}) ne s'occupe des défis
     * terminaux : laisse d'abord la saga de purge faire son travail, et ne cible que l'historique
     * antérieur à la saga ou les deadlines perdues.
     */
    Duration CHALLENGE_SWEEP_GRACE = Duration.ofMinutes(10);
}
