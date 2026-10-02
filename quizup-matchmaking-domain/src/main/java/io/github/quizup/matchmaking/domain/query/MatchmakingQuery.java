package io.github.quizup.matchmaking.domain.query;

import io.github.quizup.microservice.core.infrastructure.in.api.request.SearchRequest;

/**
 * Queries de la recherche d'appariement public.
 */
public interface MatchmakingQuery {

    record SearchMatchmakingQuery(SearchRequest request) implements MatchmakingQuery {
    }

    record GetMatchmakingByIdQuery(String matchmakingId) implements MatchmakingQuery {
    }

    record GetMatchmakingEventsQuery(String matchmakingId) implements MatchmakingQuery {
    }
}
