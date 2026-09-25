package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.microservice.core.infrastructure.in.api.request.SearchRequest;
import io.github.quizup.microservice.core.infrastructure.in.api.response.SearchResponse;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;

import java.util.concurrent.CompletableFuture;

public interface SearchLobbyUseCase {

    CompletableFuture<SearchResponse<Lobby>> search(LobbyQuery.SearchLobbyQuery query);

    default CompletableFuture<SearchResponse<Lobby>> search(SearchRequest request) {
        return search(new LobbyQuery.SearchLobbyQuery(request));
    }
}
