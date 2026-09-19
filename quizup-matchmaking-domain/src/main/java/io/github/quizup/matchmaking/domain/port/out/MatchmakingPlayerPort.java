package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.PlayerSummary;

/**
 * Port sortant inter-module : résumé joueur (nom, niveau, pays).
 */
public interface MatchmakingPlayerPort {

    PlayerSummary getPlayer(String userId);
}
