package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.port.in.GetMatchmakingUseCase;
import io.github.quizup.matchmaking.domain.query.MatchmakingQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class MatchmakingQueryService implements GetMatchmakingUseCase {

    private final QueryGateway queryGateway;

    public MatchmakingQueryService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public CompletableFuture<Matchmaking> getById(MatchmakingQuery.GetMatchmakingByIdQuery query) {
        return queryGateway.query(query, QueryResponseTypes.instanceOf(Matchmaking.class));
    }

    @Override
    public CompletableFuture<List<EventEnvelope>> getEvents(MatchmakingQuery.GetMatchmakingEventsQuery query) {
        return queryGateway.query(query, QueryResponseTypes.multipleInstancesOf(EventEnvelope.class));
    }
}
