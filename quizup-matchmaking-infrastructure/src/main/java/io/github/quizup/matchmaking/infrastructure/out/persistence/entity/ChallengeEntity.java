package io.github.quizup.matchmaking.infrastructure.out.persistence.entity;

import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Projection du défi nominatif (intention asynchrone). */
@Setter
@Getter
@Entity
@Table(name = "challenge_entry", indexes = {
        @Index(name = "idx_challenge_challenger", columnList = "challenger_id"),
        @Index(name = "idx_challenge_opponent", columnList = "opponent_id"),
        @Index(name = "idx_challenge_status", columnList = "status")
})
public class ChallengeEntity {

    @Id
    @Column(name = "challenge_id", nullable = false)
    private String challengeId;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "challenger_id", nullable = false)
    private String challengerId;

    @Column(name = "opponent_id", nullable = false)
    private String opponentId;

    @Column(name = "room_id")
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChallengeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
