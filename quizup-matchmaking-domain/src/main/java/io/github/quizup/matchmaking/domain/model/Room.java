package io.github.quizup.matchmaking.domain.model;

import lombok.Builder;

import java.time.Instant;

/**
 * Read model d'une salle (salle d'attente temps réel). L'identifiant de l'agrégat sert de
 * référence de partage (`/join/{roomId}`) — pas de code dédié.
 */
@Builder(toBuilder = true)
public record Room(
        String roomId,
        String topicId,
        String initiatorId,
        String opponentId,
        String participantId,
        String gameId,
        RoomStatus status,
        boolean initiatorPresent,
        boolean participantPresent,
        Instant allPresentAt,
        Instant readyDeadlineAt,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt
) {
}
