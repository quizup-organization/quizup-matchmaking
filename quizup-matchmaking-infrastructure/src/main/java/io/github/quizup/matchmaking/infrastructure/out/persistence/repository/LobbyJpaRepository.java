package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.LobbyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LobbyJpaRepository extends JpaRepository<LobbyEntity, String> {

    @Query("""
            select l from LobbyEntity l
            where l.status = :status
              and (l.initiatorId = :playerId or l.opponentId = :playerId)
            order by l.createdAt desc
            """)
    List<LobbyEntity> findCreatedByPlayerId(@Param("status") LobbyStatus status,
                                            @Param("playerId") String playerId);
}
