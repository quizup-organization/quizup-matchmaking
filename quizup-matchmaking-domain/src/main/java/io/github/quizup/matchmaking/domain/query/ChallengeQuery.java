package io.github.quizup.matchmaking.domain.query;

/**
 * Queries du défi nominatif.
 */
public interface ChallengeQuery {

    record GetChallengeById(String challengeId) implements ChallengeQuery {
    }

    record GetMyChallenges(String playerId) implements ChallengeQuery {
    }

    record GetChallengeEventsQuery(String challengeId) implements ChallengeQuery {
    }
}
