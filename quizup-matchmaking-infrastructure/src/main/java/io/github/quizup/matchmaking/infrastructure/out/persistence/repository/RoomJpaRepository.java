package io.github.quizup.matchmaking.infrastructure.out.persistence.repository;

import io.github.quizup.matchmaking.domain.model.RoomStatus;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomJpaRepository extends JpaRepository<RoomEntity, String> {

    @Query("""
            select r from RoomEntity r
            where r.status = :status
              and (r.initiatorId = :playerId or r.opponentId = :playerId)
            order by r.createdAt desc
            """)
    List<RoomEntity> findCreatedByPlayerId(@Param("status") RoomStatus status,
                                           @Param("playerId") String playerId);
}
