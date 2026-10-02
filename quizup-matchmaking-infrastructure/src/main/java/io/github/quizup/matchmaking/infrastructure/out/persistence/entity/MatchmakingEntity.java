package io.github.quizup.matchmaking.infrastructure.out.persistence.entity;

import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Index de recherche d'appariement (pool) doublé du read model « ticket » produit.
 */
@Setter
@Getter
@Entity
@Table(name = "matchmaking_entry", indexes = {
        @Index(name = "idx_matchmaking_pool", columnList = "topic_id, status, level, created_at"),
        @Index(name = "idx_matchmaking_player", columnList = "player_id")
})
public class MatchmakingEntity {

    @Id
    @Column(name = "matchmaking_id", nullable = false)
    private String matchmakingId;

    @Column(name = "player_id", nullable = false)
    private String playerId;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "level", nullable = false)
    private int level;

    /** Langues requises, codes ISO 639-1 séparés par des virgules (ex. {@code fr,en}). */
    @Column(name = "languages", nullable = false, length = 64)
    private String languages;

    @Column(name = "opponent_id")
    private String opponentId;

    @Column(name = "game_id")
    private String gameId;

    @Column(name = "vs_bot", nullable = false)
    private boolean vsBot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MatchmakingStatus status;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
