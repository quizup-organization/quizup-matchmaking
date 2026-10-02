package io.github.quizup.matchmaking.infrastructure.out.persistence.entity;

import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Projection du salon privé (salle d'attente). La réussite purge la ligne (aucun statut persistant).
 */
@Setter
@Getter
@Entity
@Table(name = "lobby_entry", indexes = {
        @Index(name = "idx_lobby_initiator", columnList = "initiator_id"),
        @Index(name = "idx_lobby_status", columnList = "status")
})
public class LobbyEntity {

    @Id
    @Column(name = "lobby_id", nullable = false)
    private String lobbyId;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "initiator_id", nullable = false)
    private String initiatorId;

    @Column(name = "participant_id")
    private String participantId;

    @Column(name = "game_id")
    private String gameId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LobbyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
