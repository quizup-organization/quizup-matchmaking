package io.github.quizup.matchmaking.domain.model;

import lombok.Builder;

import java.time.Instant;

/**
 * Read model « ticket » de matchmaking : vue produit du lobby interne, avec des statuts explicites
 * ({@link MatchmakingTicketStatus}) consommés tels quels par le BFF (plus d'interprétation du
 * statut interne du lobby côté façade).
 */
@Builder(toBuilder = true)
public record MatchmakingTicket(
        String ticketId,
        String topicId,
        String initiatorId,
        String opponentId,
        String gameId,
        boolean vsBot,
        MatchmakingTicketStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
