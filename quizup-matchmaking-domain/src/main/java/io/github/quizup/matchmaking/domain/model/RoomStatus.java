package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'une salle, volontairement réduit au cycle de vie.
 * <ul>
 *   <li>{@link #CREATED} — ouverte, en attente de l'apparition du second joueur.</li>
 *   <li>{@link #CLOSED} — parcours terminé normalement (partie créée, annulée, expirée).
 *       L'issue exacte est portée par l'événement terminal et la notification.</li>
 *   <li>{@link #FAILED} — issue anormale : la partie n'a pas pu être préparée.</li>
 * </ul>
 * Aucune suppression immédiate : un état terminal est purgé par la saga après rétention
 * (deadline), puis {@code markDeleted} (standard Axon).
 */
public enum RoomStatus {
    CREATED,
    CLOSED,
    FAILED
}
