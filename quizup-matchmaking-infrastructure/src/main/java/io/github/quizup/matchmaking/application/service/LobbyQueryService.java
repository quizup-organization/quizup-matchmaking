package io.github.quizup.matchmaking.application.service;

import io.github.quizup.common.domain.model.search.PageResult;
import io.github.quizup.common.infrastructure.axon.PageResponseTypes;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyEventsUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetOpenLobbiesByTopicUseCase;
import io.github.quizup.matchmaking.domain.port.in.SearchLobbyUseCase;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LobbyQueryService implements GetLobbyUseCase, GetOpenLobbiesByTopicUseCase, GetLobbyEventsUseCase, SearchLobbyUseCase {

    private final QueryGateway queryGateway;

    public LobbyQueryService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public CompletableFuture<Lobby> getById(LobbyQuery.GetLobbyById query) throws LobbyExceptions.LobbyNotFoundProblem {
        return queryGateway.query(query, ResponseTypes.instanceOf(Lobby.class));
    }

    @Override
    public CompletableFuture<List<Lobby>> getOpenByTopicId(LobbyQuery.FindOpenLobbiesByTopicId query) {
        return queryGateway.query(query, ResponseTypes.multipleInstancesOf(Lobby.class));
    }

    @Override
    public CompletableFuture<List<LobbyEvent>> getEvents(LobbyQuery.GetLobbyEventsQuery query) {
        return queryGateway.query(query, ResponseTypes.multipleInstancesOf(LobbyEvent.class));
    }

    @Override
    public CompletableFuture<PageResult<Lobby>> search(LobbyQuery.SearchLobbyQuery query) {
        return queryGateway.query(query, PageResponseTypes.pageResultOf(Lobby.class));
    }
}

