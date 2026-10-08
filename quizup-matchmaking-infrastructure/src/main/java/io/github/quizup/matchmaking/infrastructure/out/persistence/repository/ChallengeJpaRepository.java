package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.ChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChallengeJpaRepository extends JpaRepository<ChallengeEntity, String> {

    @Query("""
            select c from ChallengeEntity c
            where c.status = :status
              and (c.challengerId = :playerId or c.opponentId = :playerId)
            order by c.createdAt desc
            """)
    List<ChallengeEntity> findPendingByPlayerId(@Param("status") ChallengeStatus status,
                                                @Param("playerId") String playerId);

    @Query("""
            select c.challengeId from ChallengeEntity c
            where c.status <> :pending
              and c.resolvedAt < :before
            """)
    List<String> findTerminalIdsBefore(@Param("pending") ChallengeStatus pending,
                                       @Param("before") Instant before);
}
