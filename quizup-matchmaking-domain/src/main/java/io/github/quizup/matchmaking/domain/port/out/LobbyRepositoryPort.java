package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.Lobby;

import java.util.List;
import java.util.Optional;

public interface LobbyRepositoryPort {

    void save(Lobby lobby);

    Optional<Lobby> findById(String lobbyId);

    List<Lobby> findCreatedByPlayerId(String playerId);

    /** Salles encore ouvertes (en attente/présence) où le joueur est impliqué — présence. */
    List<Lobby> findOpenByPlayerId(String playerId);

    void deleteById(String lobbyId);
}
