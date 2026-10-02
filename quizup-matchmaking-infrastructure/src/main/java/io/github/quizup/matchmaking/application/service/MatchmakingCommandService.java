package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.port.in.MatchmakingUseCase;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class MatchmakingCommandService implements MatchmakingUseCase {

    private final CommandGateway commandGateway;

    public MatchmakingCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Override
    public CompletableFuture<String> create(MatchmakingCommand.CreateMatchmakingCommand command) {
        return commandGateway.send(command);
    }

    @Override
    public CompletableFuture<String> cancel(MatchmakingCommand.CancelMatchmakingCommand command) {
        return commandGateway.send(command);
    }
}
