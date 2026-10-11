package io.github.quizup.matchmaking.domain.query;

/**
 * Queries de la recherche d'appariement public.
 */
public interface MatchmakingQuery {

    record GetMatchmakingByIdQuery(String matchmakingId) implements MatchmakingQuery {
    }

    record GetMatchmakingEventsQuery(String matchmakingId) implements MatchmakingQuery {
    }
}
