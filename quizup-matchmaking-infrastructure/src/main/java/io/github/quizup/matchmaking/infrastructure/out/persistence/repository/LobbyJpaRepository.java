package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.LobbyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LobbyJpaRepository extends JpaRepository<LobbyEntity, String> {

    List<LobbyEntity> findByInitiatorIdAndStatusOrderByCreatedAtDesc(String initiatorId, LobbyStatus status);
}
