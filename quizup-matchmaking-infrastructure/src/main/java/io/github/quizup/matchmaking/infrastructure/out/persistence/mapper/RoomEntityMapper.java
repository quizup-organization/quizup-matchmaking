package io.github.quizup.matchmaking.infrastructure.out.persistence.mapper;

import io.github.quizup.matchmaking.domain.model.Room;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.RoomEntity;

public final class RoomEntityMapper {

    private RoomEntityMapper() {
    }

    public static Room toDomain(RoomEntity entity) {
        return Room.builder()
                .roomId(entity.getRoomId())
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
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static RoomEntity toEntity(Room room) {
        RoomEntity entity = new RoomEntity();
        entity.setRoomId(room.roomId());
        entity.setTopicId(room.topicId());
        entity.setInitiatorId(room.initiatorId());
        entity.setOpponentId(room.opponentId());
        entity.setParticipantId(room.participantId());
        entity.setGameId(room.gameId());
        entity.setStatus(room.status());
        entity.setInitiatorPresent(room.initiatorPresent());
        entity.setParticipantPresent(room.participantPresent());
        entity.setAllPresentAt(room.allPresentAt());
        entity.setReadyDeadlineAt(room.readyDeadlineAt());
        entity.setCreatedAt(room.createdAt());
        entity.setExpiresAt(room.expiresAt());
        entity.setUpdatedAt(room.updatedAt());
        return entity;
    }
}
