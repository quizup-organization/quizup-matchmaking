package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;

import java.util.concurrent.CompletableFuture;

public interface ChallengeUseCase {

    CompletableFuture<String> create(ChallengeCommand.CreateChallengeCommand command);

    CompletableFuture<String> accept(ChallengeCommand.AcceptChallengeCommand command);

    CompletableFuture<String> decline(ChallengeCommand.DeclineChallengeCommand command);

    CompletableFuture<String> cancel(ChallengeCommand.CancelChallengeCommand command);
}
