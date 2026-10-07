package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.MatchmakingEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MatchmakingJpaRepository extends JpaRepository<MatchmakingEntity, String> {

    @Query("""
            select e from MatchmakingEntity e
            where e.topicId = :topicId
              and e.status = io.github.quizup.matchmaking.domain.model.MatchmakingStatus.SEARCHING
              and e.claimedBy is null
              and e.playerId <> :excludePlayerId
              and e.level between :minLevel and :maxLevel
            order by abs(e.level - :level) asc, e.createdAt asc
            """)
    List<MatchmakingEntity> findCandidates(@Param("topicId") String topicId,
                                           @Param("excludePlayerId") String excludePlayerId,
                                           @Param("level") int level,
                                           @Param("minLevel") int minLevel,
                                           @Param("maxLevel") int maxLevel,
                                           Limit limit);

    /**
     * Claim atomique du pool : ne réussit que si la recherche est encore SEARCHING et non
     * réclamée. Le statut n'est pas modifié : la projection le passera à CLOSED à réception de
     * {@code MatchmakingMatchedEvent}.
     */
    @Modifying
    @Query("""
            update MatchmakingEntity e
            set e.claimedBy = :claimedBy,
                e.updatedAt = :now
            where e.matchmakingId = :matchmakingId
              and e.status = io.github.quizup.matchmaking.domain.model.MatchmakingStatus.SEARCHING
              and e.claimedBy is null
            """)
    int claim(@Param("matchmakingId") String matchmakingId,
              @Param("claimedBy") String claimedBy,
              @Param("now") Instant now);
}
