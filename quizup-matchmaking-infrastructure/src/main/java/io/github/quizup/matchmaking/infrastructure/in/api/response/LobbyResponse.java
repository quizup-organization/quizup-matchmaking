package io.github.quizup.matchmaking.infrastructure.in.api.response;

import io.github.quizup.matchmaking.domain.model.LobbyStatus;

import java.time.Instant;

public record LobbyResponse(
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
