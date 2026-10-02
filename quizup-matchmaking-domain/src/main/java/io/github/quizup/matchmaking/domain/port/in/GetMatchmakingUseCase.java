package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.query.MatchmakingQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetMatchmakingUseCase {

    CompletableFuture<Matchmaking> getById(MatchmakingQuery.GetMatchmakingByIdQuery query);

    CompletableFuture<List<EventEnvelope>> getEvents(MatchmakingQuery.GetMatchmakingEventsQuery query);
}
