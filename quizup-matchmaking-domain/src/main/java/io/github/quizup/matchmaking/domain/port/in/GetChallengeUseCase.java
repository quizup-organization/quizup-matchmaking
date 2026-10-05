package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.query.ChallengeQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetChallengeUseCase {

    CompletableFuture<Challenge> getById(ChallengeQuery.GetChallengeById query);

    CompletableFuture<List<Challenge>> getMyPending(ChallengeQuery.GetMyChallenges query);
}
