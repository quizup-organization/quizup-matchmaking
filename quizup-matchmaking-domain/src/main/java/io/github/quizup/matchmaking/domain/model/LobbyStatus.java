package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'un salon privé (salle d'attente). Pas de notion d'appariement ici.
 * <ul>
 *   <li>{@link #OPEN} — en attente du second joueur.</li>
 *   <li>{@link #CANCELLED} — annulé par un joueur.</li>
 *   <li>{@link #EXPIRED} — jamais rejoint dans le délai.</li>
 *   <li>{@link #FAILED} — la partie n'a pas pu être créée (échec système).</li>
 * </ul>
 * La réussite (partie créée) ne donne pas de statut : l'agrégat est purgé immédiatement.
 */
public enum LobbyStatus {
    OPEN,
    CANCELLED,
    EXPIRED,
    FAILED
}
