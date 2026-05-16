package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;

import java.util.concurrent.CompletableFuture;

public interface GetLobbyUseCase {

    CompletableFuture<Lobby> getById(LobbyQuery.GetLobbyById query) throws LobbyExceptions.LobbyNotFoundProblem;

    default CompletableFuture<Lobby> getById(String lobbyId) throws LobbyExceptions.LobbyNotFoundProblem {
        return getById(new LobbyQuery.GetLobbyById(lobbyId));
    }
}

