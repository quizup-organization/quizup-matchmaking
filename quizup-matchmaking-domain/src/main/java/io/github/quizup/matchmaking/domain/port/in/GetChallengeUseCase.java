package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.query.ChallengeQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetChallengeUseCase {

    CompletableFuture<Challenge> getById(ChallengeQuery.GetChallengeById query);

    CompletableFuture<List<Challenge>> getMyPending(ChallengeQuery.GetMyChallenges query);

    CompletableFuture<List<EventEnvelope>> getEvents(ChallengeQuery.GetChallengeEventsQuery query);
}
