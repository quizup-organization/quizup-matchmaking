package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetLobbyEventsUseCase {

    CompletableFuture<List<LobbyEvent>> getEvents(LobbyQuery.GetLobbyEventsQuery query);

    default CompletableFuture<List<LobbyEvent>> getEvents(String lobbyId) {
        return getEvents(new LobbyQuery.GetLobbyEventsQuery(lobbyId));
    }
}

