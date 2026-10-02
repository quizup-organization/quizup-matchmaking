package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;

import java.util.concurrent.CompletableFuture;

public interface MatchmakingUseCase {

    CompletableFuture<String> create(MatchmakingCommand.CreateMatchmakingCommand command);

    CompletableFuture<String> cancel(MatchmakingCommand.CancelMatchmakingCommand command);
}
