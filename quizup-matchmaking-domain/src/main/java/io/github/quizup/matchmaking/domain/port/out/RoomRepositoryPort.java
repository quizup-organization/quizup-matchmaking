package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepositoryPort {

    void save(Room room);

    Optional<Room> findById(String roomId);

    /** Salles encore ouvertes d'un joueur ({@code WAITING_PLAYERS}, initiateur ou invité). */
    List<Room> findWaitingByPlayerId(String playerId);

    void deleteById(String roomId);
}
