package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.port.out.ChallengeStorePort;
import io.github.quizup.matchmaking.domain.query.ChallengeQuery;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChallengeQueryHandler {

    private final ChallengeStorePort challengeStorePort;

    public ChallengeQueryHandler(ChallengeStorePort challengeStorePort) {
        this.challengeStorePort = challengeStorePort;
    }

    @QueryHandler
    public Challenge handle(ChallengeQuery.GetChallengeById query) {
        return challengeStorePort.findChallengeById(query.challengeId())
                .orElseThrow(() -> new ChallengeExceptions.ChallengeNotFoundProblem(query.challengeId()));
    }

    @QueryHandler
    public List<Challenge> handle(ChallengeQuery.GetMyChallenges query) {
        return challengeStorePort.findPendingByPlayerId(query.playerId());
    }
}
