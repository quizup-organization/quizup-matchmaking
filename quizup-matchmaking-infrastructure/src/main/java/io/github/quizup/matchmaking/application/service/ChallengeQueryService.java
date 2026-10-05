package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.port.in.GetChallengeUseCase;
import io.github.quizup.matchmaking.domain.query.ChallengeQuery;
import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ChallengeQueryService implements GetChallengeUseCase {

    private final QueryGateway queryGateway;

    public ChallengeQueryService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public CompletableFuture<Challenge> getById(ChallengeQuery.GetChallengeById query) {
        return queryGateway.query(query, QueryResponseTypes.instanceOf(Challenge.class));
    }

    @Override
    public CompletableFuture<List<Challenge>> getMyPending(ChallengeQuery.GetMyChallenges query) {
        return queryGateway.query(query, QueryResponseTypes.multipleInstancesOf(Challenge.class));
    }
}
