package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.RoomPlayer;

public interface ProfileRepositoryPort {
    RoomPlayer getById(String identifier);
}
