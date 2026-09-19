package io.github.quizup.matchmaking.domain.model;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record Lobby(
        String lobbyId,
        String topicId,
        String initiatorId,
        String challengerId,
        String gameId,
        boolean vsBot,
        LobbyStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

