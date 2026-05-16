package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;

import java.util.List;

public interface LobbyEventStorePort {

    List<LobbyEvent> findAllByLobbyId(String lobbyId);
}

