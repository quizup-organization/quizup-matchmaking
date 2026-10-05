package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.port.out.ChallengeEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.ChallengeRepositoryPort;
import io.github.quizup.matchmaking.domain.query.ChallengeQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChallengeQueryHandler {

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final ChallengeEventStorePort challengeEventStorePort;

    public ChallengeQueryHandler(ChallengeRepositoryPort challengeRepositoryPort,
                                 ChallengeEventStorePort challengeEventStorePort) {
        this.challengeRepositoryPort = challengeRepositoryPort;
        this.challengeEventStorePort = challengeEventStorePort;
    }

    @QueryHandler
    public Challenge handle(ChallengeQuery.GetChallengeById query) {
        return challengeRepositoryPort.findById(query.challengeId())
                .orElseThrow(() -> new ChallengeExceptions.ChallengeNotFoundProblem(query.challengeId()));
    }

    @QueryHandler
    public List<Challenge> handle(ChallengeQuery.GetMyChallenges query) {
        return challengeRepositoryPort.findPendingByPlayerId(query.playerId());
    }

    @QueryHandler
    public List<EventEnvelope> handle(ChallengeQuery.GetChallengeEventsQuery query) {
        return challengeEventStorePort.findEventEnvelopesByChallengeId(query.challengeId());
    }
}
