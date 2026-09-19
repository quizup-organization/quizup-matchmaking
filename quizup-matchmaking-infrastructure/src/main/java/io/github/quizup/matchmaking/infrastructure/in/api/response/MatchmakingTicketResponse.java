package io.github.quizup.matchmaking.infrastructure.in.api.response;

import java.io.Serializable;
import java.time.Instant;

/**
 * Vue « file d'attente » d'un lobby : le ticket de matchmaking exposé au client.
 */
public record MatchmakingTicketResponse(
        String ticketId,
        String topicId,
        String initiatorId,
        String challengerId,
        String gameId,
        boolean vsBot,
        String status,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
