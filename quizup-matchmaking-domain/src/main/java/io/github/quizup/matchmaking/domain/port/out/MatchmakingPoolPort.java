package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.MatchmakingCandidate;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant — pool de recherche d'appariement (index dérivé des recherches en cours).
 * <p>
 * Implémentation actuelle : Postgres (la projection {@code matchmaking_entry} sert d'index).
 * Un adaptateur Redis pourra remplacer cet index sans changer le domaine : l'agrégat reste la
 * source de vérité, le pool est reconstructible à partir des {@code MatchmakingStartedEvent}.
 */
public interface MatchmakingPoolPort {

    /**
     * Candidats compatibles : même sujet, niveau ±{@code levelWindow}, exclusion d'un joueur,
     * non réclamés. Les langues sont filtrées en aval (couverture par le thème).
     */
    List<MatchmakingCandidate> candidates(String topicId, int level, int levelWindow, String excludePlayerId, int limit);

    /** Réclame atomiquement une recherche ; vide si elle a déjà été prise. */
    Optional<MatchmakingCandidate> claim(String matchmakingId, String claimedByPlayerId);
}
