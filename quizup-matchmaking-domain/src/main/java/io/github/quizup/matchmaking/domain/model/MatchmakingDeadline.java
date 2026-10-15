package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines de l'appariement public (« Défier le monde »).
 */
public interface MatchmakingDeadline {

    /**
     * Fenêtre de recherche d'un adversaire humain avant **arrêt** (statut {@code EXPIRED}). Aucun
     * repli bot automatique : passé le délai, le client propose de relancer une recherche ou de
     * basculer explicitement sur un entraînement contre un robot (sans XP). L'échéance est aussi
     * exposée au client ({@code deadlineAt}) pour le compte à rebours et son tic-tac.
     */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(10);

    /** Rétention d'un ticket d'appariement terminal avant purge (read model). */
    String MATCHMAKING_PURGE = "matchmaking-purge";

    Duration MATCHMAKING_RETENTION_DURATION = Duration.ofMinutes(2);
}
