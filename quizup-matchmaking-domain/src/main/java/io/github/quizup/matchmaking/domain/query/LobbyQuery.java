package io.github.quizup.matchmaking.domain.query;

import io.github.quizup.microservice.core.infrastructure.in.api.request.SearchRequest;

/**
 * Marker interface pour les queries du domaine Lobby.
 */
public interface LobbyQuery {

    record SearchLobbyQuery(SearchRequest request) implements LobbyQuery {
    }

    record FindFirstOpenLobbyByTopicId(String topicId) implements LobbyQuery {
    }

    record FindOpenLobbiesByTopicId(String topicId) implements LobbyQuery {
    }

    record FindLobbyById(String lobbyId) implements LobbyQuery {
    }

    record GetLobbyById(String lobbyId) implements LobbyQuery {
    }

    record GetLobbyEventsQuery(String lobbyId) implements LobbyQuery {
    }
}
