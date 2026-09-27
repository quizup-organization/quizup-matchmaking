package io.github.quizup.matchmaking.domain.model;

/**
 * Statut produit d'un ticket de matchmaking (contrat BFF/web) :
 * {@code SEARCHING} tant qu'aucun adversaire n'est trouvé, {@code MATCHED} quand une partie est
 * créée (humain ou bot), {@code CANCELLED} à l'annulation/expiration.
 */
public enum MatchmakingTicketStatus {
    SEARCHING,
    MATCHED,
    CANCELLED
}
