package io.github.quizup.matchmaking.domain.query;

import io.github.quizup.microservice.core.domain.model.search.FilterCriteria;
import io.github.quizup.microservice.core.domain.model.search.PageCriteria;
import io.github.quizup.microservice.core.domain.model.search.SortCriteria;
import io.github.quizup.microservice.core.domain.query.SearchQuery;

import java.util.List;

/**
 * Marker interface pour les queries du domaine Lobby.
 */
public interface LobbyQuery {

    record SearchLobbyQuery(
            List<FilterCriteria> filters,
            List<SortCriteria> sorts,
            PageCriteria page
    ) implements LobbyQuery, SearchQuery {
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
