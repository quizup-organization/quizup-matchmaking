package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetOpenLobbiesByTopicUseCase {

    CompletableFuture<List<Lobby>> getOpenByTopicId(LobbyQuery.FindOpenLobbiesByTopicId query);

    default CompletableFuture<List<Lobby>> getOpenByTopicId(String topicId) {
        return getOpenByTopicId(new LobbyQuery.FindOpenLobbiesByTopicId(topicId));
    }
}

