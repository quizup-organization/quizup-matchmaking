package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

/**
 * Joueur résolu depuis quizup-profile (nom, langue, progression à l'instant présent).
 */
public record RoomPlayer(
        String playerId,
        String playerName,
        Language language,
        int level,
        int xpTotal
) {
}
