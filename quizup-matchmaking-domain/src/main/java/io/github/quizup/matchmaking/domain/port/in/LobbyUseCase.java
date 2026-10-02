package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;

import java.util.concurrent.CompletableFuture;

public interface LobbyUseCase {

    CompletableFuture<String> create(LobbyCommand.CreateLobbyCommand command);

    CompletableFuture<String> join(LobbyCommand.JoinLobbyCommand command);

    CompletableFuture<String> leave(LobbyCommand.LeaveLobbyCommand command);

    CompletableFuture<String> cancel(LobbyCommand.CancelLobbyCommand command);
}
