package io.github.quizup.matchmaking.infrastructure.in.api.mapper;

import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.infrastructure.in.api.response.MatchmakingTicketResponse;

public final class MatchmakingResponseMapper {

    private MatchmakingResponseMapper() {
    }

    public static MatchmakingTicketResponse toTicket(Lobby lobby) {
        return new MatchmakingTicketResponse(
                lobby.lobbyId(),
                lobby.topicId(),
                lobby.initiatorId(),
                lobby.challengerId(),
                lobby.gameId(),
                lobby.vsBot(),
                mapStatus(lobby.status()),
                lobby.createdAt(),
                lobby.updatedAt()
        );
    }

    private static String mapStatus(LobbyStatus status) {
        return switch (status) {
            case OPEN -> "WAITING";
            case COMPLETED -> "MATCHED";
            case CANCELLED -> "CANCELLED";
        };
    }
}
