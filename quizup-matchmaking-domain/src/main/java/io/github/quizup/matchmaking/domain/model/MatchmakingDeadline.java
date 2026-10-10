package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines de l'appariement public (« Défier le monde »).
 */
public interface MatchmakingDeadline {

    /** Recherche d'un adversaire humain avant bascule sur une partie bot. */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(5);

    /** Rétention d'un ticket d'appariement terminal avant purge (read model). */
    String MATCHMAKING_PURGE = "matchmaking-purge";

    Duration MATCHMAKING_RETENTION_DURATION = Duration.ofMinutes(2);
}
