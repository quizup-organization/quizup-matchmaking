package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import io.github.quizup.microservice.core.domain.model.notification.NotificationEnvelope;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetLobbyEventsUseCase {

    CompletableFuture<List<NotificationEnvelope<LobbyEvent>>> getEvents(LobbyQuery.GetLobbyEventsQuery query);

    default CompletableFuture<List<NotificationEnvelope<LobbyEvent>>> getEvents(String lobbyId) {
        return getEvents(new LobbyQuery.GetLobbyEventsQuery(lobbyId));
    }
}
