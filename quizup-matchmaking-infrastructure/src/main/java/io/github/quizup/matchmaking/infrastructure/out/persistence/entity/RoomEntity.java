package io.github.quizup.matchmaking.infrastructure.out.persistence.entity;

import io.github.quizup.matchmaking.domain.model.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Projection de la salle (salle d'attente temps réel). Les états terminaux sont conservés le
 * temps de la rétention puis purgés par la saga.
 */
@Setter
@Getter
@Entity
@Table(name = "room_entry", indexes = {
        @Index(name = "idx_room_initiator", columnList = "initiator_id"),
        @Index(name = "idx_room_opponent", columnList = "opponent_id"),
        @Index(name = "idx_room_participant", columnList = "participant_id"),
        @Index(name = "idx_room_status", columnList = "status")
})
public class RoomEntity {

    @Id
    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "initiator_id", nullable = false)
    private String initiatorId;

    @Column(name = "opponent_id")
    private String opponentId;

    @Column(name = "participant_id")
    private String participantId;

    @Column(name = "game_id")
    private String gameId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "initiator_present", nullable = false)
    private boolean initiatorPresent;

    @Column(name = "participant_present", nullable = false)
    private boolean participantPresent;

    @Column(name = "all_present_at")
    private Instant allPresentAt;

    @Column(name = "ready_deadline_at")
    private Instant readyDeadlineAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
