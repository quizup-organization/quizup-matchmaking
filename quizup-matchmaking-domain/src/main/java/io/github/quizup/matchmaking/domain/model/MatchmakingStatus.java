package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'une recherche d'appariement (duel public), volontairement réduit au cycle de vie.
 * <ul>
 *   <li>{@link #SEARCHING} — en recherche d'un adversaire.</li>
 *   <li>{@link #CLOSED} — parcours terminé normalement (adversaire trouvé, recherche annulée).
 *       L'issue exacte est portée par l'événement terminal et la notification.</li>
 *   <li>{@link #FAILED} — issue anormale : la partie n'a pas pu être créée.</li>
 * </ul>
 * Aucune suppression immédiate : un état terminal est purgé par la saga après rétention
 * (deadline), puis {@code markDeleted} (standard Axon).
 */
public enum MatchmakingStatus {
    SEARCHING,
    CLOSED,
    FAILED
}
