package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;

/**
 * Règles métier du cycle de vie d'un lobby.
 */
public final class LobbyPolicy {

    private LobbyPolicy() {}

    public static final String BOT_PLAYER_ID = QuizUpConstants.BOT_USER_ID;

    /** Détermine si un bot doit être injecté comme challenger. */
    public static boolean shouldFallbackToBot(String challengerId) {
        return challengerId == null || challengerId.isBlank();
    }
}

