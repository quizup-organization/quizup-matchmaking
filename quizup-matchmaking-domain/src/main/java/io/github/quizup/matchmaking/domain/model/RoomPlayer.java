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
        int xpTotal,
        /** XP cumulée du joueur dans le sujet de la salle (difficulté des questions). */
        int topicXp,
        /** Elo au moment de l'entrée en salle (snapshot porté par la partie). */
        Integer rating
) {

    /** Compatibilité avec les snapshots historiques sans Elo. */
    public RoomPlayer(String playerId, String playerName, Language language,
                      int level, int xpTotal, int topicXp) {
        this(playerId, playerName, language, level, xpTotal, topicXp, null);
    }
}
