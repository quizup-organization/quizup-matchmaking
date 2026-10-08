package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.PlayerSummary;

/**
 * Port sortant inter-module : résumé joueur (nom, niveau, pays) via {@code quizup-profile}.
 * La disponibilité temps réel n'est pas un critère d'appariement : le pool ne contient que des
 * tickets de recherche actifs, l'agrégat reste la source de vérité.
 */
public interface MatchmakingPlayerPort {

    PlayerSummary getPlayer(String userId);
}
