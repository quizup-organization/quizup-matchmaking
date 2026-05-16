package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.port.in.CancelLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.JoinLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.OpenLobbyUseCase;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class LobbyCommandService implements OpenLobbyUseCase, JoinLobbyUseCase, CancelLobbyUseCase {

    private final CommandGateway commandGateway;

    public LobbyCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Override
    public CompletableFuture<String> open(LobbyCommand.OpenLobbyCommand command) {
        return commandGateway.send(command);
    }

    @Override
    public CompletableFuture<String> join(LobbyCommand.JoinLobbyCommand command) {
        return commandGateway.send(command);
    }

    @Override
    public CompletableFuture<String> cancel(LobbyCommand.CancelLobbyCommand command) {
        return commandGateway.send(command);
    }
}

