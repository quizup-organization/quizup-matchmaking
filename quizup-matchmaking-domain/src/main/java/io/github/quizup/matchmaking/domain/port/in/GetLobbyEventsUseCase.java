package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetLobbyEventsUseCase {

    CompletableFuture<List<EventEnvelope>> getEvents(LobbyQuery.GetLobbyEventsQuery query);

    default CompletableFuture<List<EventEnvelope>> getEvents(String lobbyId) {
        return getEvents(new LobbyQuery.GetLobbyEventsQuery(lobbyId));
    }
}
