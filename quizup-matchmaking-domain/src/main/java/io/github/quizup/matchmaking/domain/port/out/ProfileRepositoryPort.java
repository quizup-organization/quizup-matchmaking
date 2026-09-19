package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.LobbyPlayer;

public interface ProfileRepositoryPort {
    LobbyPlayer getById(String identifier);
}
