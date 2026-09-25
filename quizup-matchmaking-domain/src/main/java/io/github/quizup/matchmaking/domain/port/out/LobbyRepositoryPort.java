package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.microservice.core.infrastructure.in.api.request.SearchRequest;
import io.github.quizup.microservice.core.infrastructure.in.api.response.SearchResponse;
import io.github.quizup.matchmaking.domain.model.Lobby;

import java.util.List;
import java.util.Optional;

public interface LobbyRepositoryPort {

    void save(Lobby lobby);

    Optional<Lobby> findById(String lobbyId);

    Optional<Lobby> findFirstOpenByTopicId(String topicId);

    List<Lobby> findOpenByTopicId(String topicId);

    SearchResponse<Lobby> findAll(SearchRequest request);

    void deleteById(String lobbyId);
}

