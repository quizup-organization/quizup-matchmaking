package io.github.quizup.matchmaking.infrastructure.in.api.mapper;

import io.github.quizup.common.domain.model.search.PageResult;
import io.github.quizup.common.infrastructure.in.api.response.PageResponse;
import io.github.quizup.common.infrastructure.mapper.SearchResponseMapper;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.infrastructure.in.api.response.LobbyResponse;

import java.util.List;

public final class LobbyResponseMapper {

    private LobbyResponseMapper() {
    }

    public static LobbyResponse toResponse(Lobby lobby) {
        return new LobbyResponse(
                lobby.lobbyId(),
                lobby.topicId(),
                lobby.initiatorId(),
                lobby.challengerId(),
                lobby.gameId(),
                lobby.vsBot(),
                lobby.status(),
                lobby.createdAt(),
                lobby.updatedAt()
        );
    }

    public static List<LobbyResponse> toResponse(List<Lobby> lobbies) {
        return lobbies.stream()
                .map(LobbyResponseMapper::toResponse)
                .toList();
    }

    public static PageResponse<LobbyResponse> toResponse(PageResult<Lobby> pageResult) {
        return SearchResponseMapper.toSearchResponse(pageResult, LobbyResponseMapper::toResponse);
    }
}

