package io.github.quizup.matchmaking.infrastructure.out.persistence.entity;

import io.github.quizup.matchmaking.domain.model.MatchmakingTicketStatus;
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

/**
 * Entité JPA du read model « ticket » de matchmaking.
 */
@Getter
@Setter
@Entity
@Table(name = "matchmaking_ticket_entry", indexes = {
        @Index(name = "idx_ticket_initiator", columnList = "initiator_id"),
        @Index(name = "idx_ticket_status", columnList = "status")
})
public class MatchmakingTicketEntity {

    @Id
    @Column(name = "ticket_id", nullable = false)
    private String ticketId;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "initiator_id", nullable = false)
    private String initiatorId;

    @Column(name = "opponent_id")
    private String opponentId;

    @Column(name = "game_id")
    private String gameId;

    @Column(name = "vs_bot", nullable = false)
    private boolean vsBot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MatchmakingTicketStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
