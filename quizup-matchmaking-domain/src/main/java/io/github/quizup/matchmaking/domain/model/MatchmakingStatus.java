package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'une recherche d'appariement (duel public).
 * <ul>
 *   <li>{@link #SEARCHING} — en recherche d'un adversaire.</li>
 *   <li>{@link #MATCHED} — adversaire trouvé (humain ou bot), partie créée.</li>
 *   <li>{@link #CANCELLED} — recherche annulée par le joueur.</li>
 *   <li>{@link #FAILED} — la partie n'a pas pu être créée (échec système).</li>
 * </ul>
 */
public enum MatchmakingStatus {
    SEARCHING,
    MATCHED,
    CANCELLED,
    FAILED
}
