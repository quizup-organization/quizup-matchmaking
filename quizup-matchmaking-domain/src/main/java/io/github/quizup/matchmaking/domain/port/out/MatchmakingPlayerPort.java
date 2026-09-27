package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.PlayerSummary;

import java.util.Collection;
import java.util.Set;

/**
 * Port sortant inter-module : résumé joueur (nom, niveau, pays) et disponibilité temps réel
 * (présence) pour n'apparier que des joueurs effectivement en ligne.
 */
public interface MatchmakingPlayerPort {

    PlayerSummary getPlayer(String userId);

    /**
     * Sous-ensemble des joueurs en ligne parmi les ids fournis (les inconnus sont absents).
     */
    Set<String> filterOnline(Collection<String> userIds);

    /** Disponibilité temps réel d'un joueur (faux si inconnu ou hors ligne). */
    default boolean isOnline(String userId) {
        return filterOnline(Set.of(userId)).contains(userId);
    }
}
