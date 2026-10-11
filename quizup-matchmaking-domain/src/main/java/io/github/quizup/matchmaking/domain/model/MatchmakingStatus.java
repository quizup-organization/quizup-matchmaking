package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'une recherche d'appariement (duel public), volontairement réduit au cycle de vie.
 * <ul>
 *   <li>{@link #SEARCHING} — en recherche d'un adversaire.</li>
 *   <li>{@link #COMPLETED} — appariement abouti : la salle a été créée (adversaire humain ou
 *       bot).</li>
 *   <li>{@link #CANCELLED} — recherche annulée par le joueur (pas de salle).</li>
 *   <li>{@link #FAILED} — issue anormale : la salle n'a pas pu être créée.</li>
 * </ul>
 * Aucune suppression immédiate : un état terminal est purgé par la saga après rétention
 * (deadline), puis {@code markDeleted} (standard Axon).
 */
public enum MatchmakingStatus {
    SEARCHING,
    COMPLETED,
    CANCELLED,
    FAILED
}
