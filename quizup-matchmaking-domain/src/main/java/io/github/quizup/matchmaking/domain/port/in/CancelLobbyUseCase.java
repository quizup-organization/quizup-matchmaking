package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;

import java.util.concurrent.CompletableFuture;

public interface CancelLobbyUseCase {

    CompletableFuture<String> cancel(LobbyCommand.CancelLobbyCommand command);

    default CompletableFuture<String> cancel(String lobbyId, String initiatorId) {
        return cancel(new LobbyCommand.CancelLobbyCommand(lobbyId, initiatorId));
    }

    default void cancelAndWait(String lobbyId, String initiatorId) {
        cancel(lobbyId, initiatorId).join();
    }
}

