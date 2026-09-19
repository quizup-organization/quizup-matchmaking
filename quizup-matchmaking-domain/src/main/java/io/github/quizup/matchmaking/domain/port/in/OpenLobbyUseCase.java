package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;

import java.util.concurrent.CompletableFuture;

public interface OpenLobbyUseCase {

    CompletableFuture<String> open(LobbyCommand.OpenLobbyCommand command);

    default CompletableFuture<String> open(String lobbyId, String initiatorId, String topicId) {
        return open(new LobbyCommand.OpenLobbyCommand(lobbyId, initiatorId, topicId));
    }

    default void openAndWait(String lobbyId, String initiatorId, String topicId) {
        open(lobbyId, initiatorId, topicId).join();
    }
}

