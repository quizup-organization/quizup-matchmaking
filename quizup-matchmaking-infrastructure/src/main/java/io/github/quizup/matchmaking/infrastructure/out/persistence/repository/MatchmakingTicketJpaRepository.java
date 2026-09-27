package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.MatchmakingTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchmakingTicketJpaRepository extends JpaRepository<MatchmakingTicketEntity, String> {
}
