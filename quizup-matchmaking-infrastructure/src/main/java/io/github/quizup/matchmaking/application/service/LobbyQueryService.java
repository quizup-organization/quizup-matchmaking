package io.github.quizup.matchmaking.application.service;

import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.microservice.core.domain.model.notification.NotificationEnvelope;
import io.github.quizup.microservice.core.domain.model.search.PageResult;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyEventsUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetOpenLobbiesByTopicUseCase;
import io.github.quizup.matchmaking.domain.port.in.SearchLobbyUseCase;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
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
        return queryGateway.query(query, QueryResponseTypes.instanceOf(Lobby.class));
    }

    @Override
    public CompletableFuture<List<Lobby>> getOpenByTopicId(LobbyQuery.FindOpenLobbiesByTopicId query) {
        return queryGateway.query(query, QueryResponseTypes.multipleInstancesOf(Lobby.class));
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<NotificationEnvelope<LobbyEvent>>> getEvents(LobbyQuery.GetLobbyEventsQuery query) {
        return queryGateway
                .query(query, QueryResponseTypes.multipleInstancesOf(NotificationEnvelope.class))
                .thenApply(result -> (List<NotificationEnvelope<LobbyEvent>>) (List<?>) result);
    }

    @Override
    public CompletableFuture<PageResult<Lobby>> search(LobbyQuery.SearchLobbyQuery query) {
        return queryGateway.query(query, QueryResponseTypes.pageResultOf(Lobby.class));
    }
}

