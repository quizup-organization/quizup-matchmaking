package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepositoryPort {

    void save(Room room);

    Optional<Room> findById(String roomId);

    List<Room> findCreatedByPlayerId(String playerId);

    void deleteById(String roomId);
}
