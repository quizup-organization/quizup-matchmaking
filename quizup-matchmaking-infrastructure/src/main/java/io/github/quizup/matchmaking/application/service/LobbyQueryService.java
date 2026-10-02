package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyUseCase;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LobbyQueryService implements GetLobbyUseCase {

    private final QueryGateway queryGateway;

    public LobbyQueryService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public CompletableFuture<Lobby> getById(LobbyQuery.GetLobbyById query) {
        return queryGateway.query(query, QueryResponseTypes.instanceOf(Lobby.class));
    }

    @Override
    public CompletableFuture<List<Lobby>> getMyOpen(LobbyQuery.GetMyOpenLobbies query) {
        return queryGateway.query(query, QueryResponseTypes.multipleInstancesOf(Lobby.class));
    }

    @Override
    public CompletableFuture<List<EventEnvelope>> getEvents(LobbyQuery.GetLobbyEventsQuery query) {
        return queryGateway.query(query, QueryResponseTypes.multipleInstancesOf(EventEnvelope.class));
    }
}
