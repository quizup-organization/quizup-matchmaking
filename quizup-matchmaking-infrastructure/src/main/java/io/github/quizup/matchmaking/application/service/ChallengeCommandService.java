package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.port.in.ChallengeUseCase;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ChallengeCommandService implements ChallengeUseCase {

    private final CommandGateway commandGateway;

    public ChallengeCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Override
    public CompletableFuture<String> create(ChallengeCommand.CreateChallengeCommand command) {
        return commandGateway.send(command);
    }

    @Override
    public CompletableFuture<String> accept(ChallengeCommand.AcceptChallengeCommand command) {
        return commandGateway.send(command);
    }

    @Override
    public CompletableFuture<String> decline(ChallengeCommand.DeclineChallengeCommand command) {
        return commandGateway.send(command);
    }

    @Override
    public CompletableFuture<String> cancel(ChallengeCommand.CancelChallengeCommand command) {
        return commandGateway.send(command);
    }
}
