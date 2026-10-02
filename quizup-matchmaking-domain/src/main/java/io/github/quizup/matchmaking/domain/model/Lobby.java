package io.github.quizup.matchmaking.domain.model;

import lombok.Builder;

import java.time.Instant;

/**
 * Read model d'un salon privé (salle d'attente). L'identifiant de l'agrégat sert de référence
 * de partage (`/join/{lobbyId}`) — pas de code dédié.
 */
@Builder(toBuilder = true)
public record Lobby(
        String lobbyId,
        String topicId,
        String initiatorId,
        String participantId,
        String gameId,
        LobbyStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt
) {
}
