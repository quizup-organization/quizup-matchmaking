package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.LobbyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LobbyJpaRepository extends JpaRepository<LobbyEntity, String>, JpaSpecificationExecutor<LobbyEntity> {

    Optional<LobbyEntity> findFirstByTopicIdAndStatusOrderByCreatedAtAsc(String topicId, LobbyStatus status);

    List<LobbyEntity> findByTopicIdAndStatusOrderByCreatedAtAsc(String topicId, LobbyStatus status);
}

