package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingStorePort;
import io.github.quizup.matchmaking.domain.query.MatchmakingQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MatchmakingQueryHandler {

    private final MatchmakingStorePort matchmakingStorePort;
    private final MatchmakingEventStorePort matchmakingEventStorePort;

    public MatchmakingQueryHandler(MatchmakingStorePort matchmakingStorePort,
                                   MatchmakingEventStorePort matchmakingEventStorePort) {
        this.matchmakingStorePort = matchmakingStorePort;
        this.matchmakingEventStorePort = matchmakingEventStorePort;
    }

    @QueryHandler
    public Matchmaking handle(MatchmakingQuery.GetMatchmakingByIdQuery query) {
        return matchmakingStorePort.findById(query.matchmakingId())
                .orElseThrow(() -> new LobbyExceptions.MatchmakingNotFoundProblem(query.matchmakingId()));
    }

    @QueryHandler
    public List<EventEnvelope> handle(MatchmakingQuery.GetMatchmakingEventsQuery query) {
        return matchmakingEventStorePort.findEventEnvelopesByMatchmakingId(query.matchmakingId());
    }
}
