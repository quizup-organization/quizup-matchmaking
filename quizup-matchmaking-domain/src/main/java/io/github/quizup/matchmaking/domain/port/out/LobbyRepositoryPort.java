package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.common.domain.model.search.PageResult;
import io.github.quizup.common.domain.model.search.SearchCriteria;
import io.github.quizup.matchmaking.domain.model.Lobby;

import java.util.List;
import java.util.Optional;

public interface LobbyRepositoryPort {

    void save(Lobby lobby);

    Optional<Lobby> findById(String lobbyId);

    Optional<Lobby> findFirstOpenByTopicId(String topicId);

    List<Lobby> findOpenByTopicId(String topicId);

    PageResult<Lobby> findAll(SearchCriteria searchCriteria);

    void deleteById(String lobbyId);
}

