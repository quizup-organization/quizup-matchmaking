package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines de l'appariement public (« Défier le monde »).
 */
public interface MatchmakingDeadline {

    /**
     * Recherche d'un adversaire humain avant bascule sur une partie d'**entraînement** (bot).
     * 10 s : la partie qui suit n'attribue aucune XP, on laisse donc au joueur le temps de
     * trouver un adversaire réel (c'est la seule voie de progression).
     */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(10);

    /** Rétention d'un ticket d'appariement terminal avant purge (read model). */
    String MATCHMAKING_PURGE = "matchmaking-purge";

    Duration MATCHMAKING_RETENTION_DURATION = Duration.ofMinutes(2);
}
