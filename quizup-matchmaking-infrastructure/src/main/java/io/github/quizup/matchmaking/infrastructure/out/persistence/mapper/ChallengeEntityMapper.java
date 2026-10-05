package io.github.quizup.matchmaking.infrastructure.out.persistence.mapper;

import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.ChallengeEntity;

public final class ChallengeEntityMapper {

    private ChallengeEntityMapper() {
    }

    public static Challenge toDomain(ChallengeEntity entity) {
        return Challenge.builder()
                .challengeId(entity.getChallengeId())
                .topicId(entity.getTopicId())
                .challengerId(entity.getChallengerId())
                .opponentId(entity.getOpponentId())
                .roomId(entity.getRoomId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }

    public static ChallengeEntity toEntity(Challenge challenge) {
        ChallengeEntity entity = new ChallengeEntity();
        entity.setChallengeId(challenge.challengeId());
        entity.setTopicId(challenge.topicId());
        entity.setChallengerId(challenge.challengerId());
        entity.setOpponentId(challenge.opponentId());
        entity.setRoomId(challenge.roomId());
        entity.setStatus(challenge.status());
        entity.setCreatedAt(challenge.createdAt());
        entity.setExpiresAt(challenge.expiresAt());
        entity.setResolvedAt(challenge.resolvedAt());
        return entity;
    }
}
