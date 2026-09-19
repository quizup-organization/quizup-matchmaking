package io.github.quizup.matchmaking.domain.model;

/**
 * Règles d'appariement (features.md §6) : écart de niveau maximal, préférence
 * géographique, fallback bot.
 */
public interface MatchmakingRules {

    /** Fenêtre de niveau acceptée entre deux adversaires. */
    int LEVEL_WINDOW = 5;
}
