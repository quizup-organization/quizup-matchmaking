package io.github.quizup.matchmaking.infrastructure.out.persistence.entity;

import io.github.quizup.microservice.core.domain.model.search.FieldType;
import io.github.quizup.microservice.core.domain.model.search.Searchable;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "lobby_entry", indexes = {
        @Index(name = "idx_lobby_topic_status_created", columnList = "topic_id, status, created_at"),
        @Index(name = "idx_lobby_initiator",  columnList = "initiator_id"),
        @Index(name = "idx_lobby_challenger", columnList = "challenger_id")
})
public class LobbyEntity {

    @Id
    @Searchable(type = FieldType.STRING)
    @Column(name = "lobby_id", nullable = false)
    private String lobbyId;

    @Searchable(type = FieldType.STRING)
    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Searchable(type = FieldType.STRING)
    @Column(name = "initiator_id", nullable = false)
    private String initiatorId;

    @Searchable(type = FieldType.STRING)
    @Column(name = "challenger_id")
    private String challengerId;

    @Searchable(type = FieldType.STRING)
    @Column(name = "game_id")
    private String gameId;

    @Column(name = "vs_bot")
    private boolean vsBot;

    @Searchable(type = FieldType.STRING)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LobbyStatus status;

    @Searchable(type = FieldType.DATE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Searchable(type = FieldType.DATE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


}
