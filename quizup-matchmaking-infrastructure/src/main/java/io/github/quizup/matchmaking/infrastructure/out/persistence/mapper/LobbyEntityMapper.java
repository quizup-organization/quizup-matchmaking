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
                .opponentId(entity.getOpponentId())
                .participantId(entity.getParticipantId())
                .gameId(entity.getGameId())
                .status(entity.getStatus())
                .initiatorPresent(entity.isInitiatorPresent())
                .participantPresent(entity.isParticipantPresent())
                .allPresentAt(entity.getAllPresentAt())
                .readyDeadlineAt(entity.getReadyDeadlineAt())
                .missedReason(entity.getMissedReason())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static LobbyEntity toEntity(Lobby lobby) {
        LobbyEntity entity = new LobbyEntity();
        entity.setLobbyId(lobby.lobbyId());
        entity.setTopicId(lobby.topicId());
        entity.setInitiatorId(lobby.initiatorId());
        entity.setOpponentId(lobby.opponentId());
        entity.setParticipantId(lobby.participantId());
        entity.setGameId(lobby.gameId());
        entity.setStatus(lobby.status());
        entity.setInitiatorPresent(lobby.initiatorPresent());
        entity.setParticipantPresent(lobby.participantPresent());
        entity.setAllPresentAt(lobby.allPresentAt());
        entity.setReadyDeadlineAt(lobby.readyDeadlineAt());
        entity.setMissedReason(lobby.missedReason());
        entity.setCreatedAt(lobby.createdAt());
        entity.setExpiresAt(lobby.expiresAt());
        entity.setUpdatedAt(lobby.updatedAt());
        return entity;
    }
}
