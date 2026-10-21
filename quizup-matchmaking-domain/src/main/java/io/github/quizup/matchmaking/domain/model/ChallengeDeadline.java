package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines du défi nominatif (intention asynchrone). La salle a ses propres
 * deadlines ({@link RoomDeadline}).
 */
public interface ChallengeDeadline {

    /** Expiration d'un défi jamais accepté. */
    String CHALLENGE_EXPIRY = "challenge-expiry";

    Duration CHALLENGE_EXPIRY_DURATION = Duration.ofHours(1);

    /**
     * Durée d'une demande de revanche (défi portant le {@code gameId} d'une partie terminée) :
     * très courte, car le lanceur attend sur le bouton « Revanche » de l'écran de résultat.
     */
    Duration REMATCH_EXPIRY_DURATION = Duration.ofSeconds(10);

    /** Durée d'expiration selon la nature du défi. */
    static Duration expiryFor(boolean rematch) {
        return rematch ? REMATCH_EXPIRY_DURATION : CHALLENGE_EXPIRY_DURATION;
    }

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
