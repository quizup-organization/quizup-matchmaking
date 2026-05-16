package io.github.quizup.matchmaking.infrastructure.out.persistence.mapper;

import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.LobbyEntity;

public final class LobbyEntityMapper {

    private LobbyEntityMapper() {
    }

    public static Lobby toDomain(LobbyEntity entity) {
        return Lobby.builder()
                .lobbyId(entity.getLobbyId())
                .topicId(entity.getTopicId())
                .initiatorId(entity.getInitiatorId())
                .challengerId(entity.getChallengerId())
                .gameId(entity.getGameId())
                .vsBot(entity.isVsBot())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static LobbyEntity toEntity(Lobby lobby) {
        LobbyEntity entity = new LobbyEntity();
        entity.setLobbyId(lobby.lobbyId());
        entity.setTopicId(lobby.topicId());
        entity.setInitiatorId(lobby.initiatorId());
        entity.setChallengerId(lobby.challengerId());
        entity.setGameId(lobby.gameId());
        entity.setVsBot(lobby.vsBot());
        entity.setStatus(lobby.status());
        entity.setCreatedAt(lobby.createdAt());
        entity.setUpdatedAt(lobby.updatedAt());
        return entity;
    }
}

