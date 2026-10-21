package io.github.quizup.matchmaking.domain.model;

/**
 * Règles d'appariement (features.md §6) : écart d'Elo maximal, préférence
 * géographique, fallback bot.
 */
public interface MatchmakingRules {

    /** Fenêtre d'Elo acceptée entre deux adversaires (large au démarrage de l'échelle). */
    int RATING_WINDOW = 400;
}
