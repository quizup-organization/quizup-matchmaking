package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetLobbyUseCase {

    CompletableFuture<Lobby> getById(LobbyQuery.GetLobbyById query);

    CompletableFuture<List<Lobby>> getMyOpen(LobbyQuery.GetMyOpenLobbies query);

    CompletableFuture<List<EventEnvelope>> getEvents(LobbyQuery.GetLobbyEventsQuery query);
}
