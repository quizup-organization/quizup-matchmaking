package io.github.quizup.matchmaking.domain.query;

import io.github.quizup.microservice.core.infrastructure.in.api.request.SearchRequest;

/**
 * Queries du salon privé.
 */
public interface LobbyQuery {

    record SearchLobbyQuery(SearchRequest request) implements LobbyQuery {
    }

    record FindLobbyById(String lobbyId) implements LobbyQuery {
    }

    record GetLobbyById(String lobbyId) implements LobbyQuery {
    }

    record GetMyOpenLobbies(String playerId) implements LobbyQuery {
    }

    record GetLobbyEventsQuery(String lobbyId) implements LobbyQuery {
    }
}
