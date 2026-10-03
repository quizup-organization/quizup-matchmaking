package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'un salon privé (salle d'attente), volontairement réduit au cycle de vie.
 * <ul>
 *   <li>{@link #CREATED} — en attente du second joueur.</li>
 *   <li>{@link #CLOSED} — parcours terminé normalement (partie créée, annulé, refusé, expiré).
 *       L'issue exacte est portée par l'événement terminal et la notification.</li>
 *   <li>{@link #FAILED} — issue anormale : la partie n'a pas pu être créée.</li>
 * </ul>
 * Aucune suppression immédiate : un état terminal est purgé par la saga après rétention
 * (deadline), puis {@code markDeleted} (standard Axon).
 */
public enum LobbyStatus {
    CREATED,
    CLOSED,
    FAILED
}
