package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.MatchmakingExceptions;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingRepositoryPort;
import io.github.quizup.matchmaking.domain.query.MatchmakingQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MatchmakingQueryHandler {

    private final MatchmakingRepositoryPort matchmakingRepositoryPort;
    private final MatchmakingEventStorePort matchmakingEventStorePort;

    public MatchmakingQueryHandler(MatchmakingRepositoryPort matchmakingRepositoryPort,
                                   MatchmakingEventStorePort matchmakingEventStorePort) {
        this.matchmakingRepositoryPort = matchmakingRepositoryPort;
        this.matchmakingEventStorePort = matchmakingEventStorePort;
    }

    @QueryHandler
    public Matchmaking handle(MatchmakingQuery.GetMatchmakingByIdQuery query) {
        return matchmakingRepositoryPort.findById(query.matchmakingId())
                .orElseThrow(() -> new MatchmakingExceptions.MatchmakingNotFoundProblem(query.matchmakingId()));
    }

    @QueryHandler
    public List<EventEnvelope> handle(MatchmakingQuery.GetMatchmakingEventsQuery query) {
        return matchmakingEventStorePort.findEventEnvelopesByMatchmakingId(query.matchmakingId());
    }
}
