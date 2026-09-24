package io.github.quizup.matchmaking.application.handler.command;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.port.in.MatchmakingUseCase;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Expose la file d'attente de matchmaking sur le bus de commandes distribué : le BFF
 * (façade unique) dispatche {@link MatchmakingCommand.EnqueuePlayerCommand} /
 * {@link MatchmakingCommand.CancelMatchmakingCommand} au lieu d'appeler le use case via REST.
 */
@Component
public class MatchmakingCommandHandler {

    private final MatchmakingUseCase matchmakingUseCase;

    public MatchmakingCommandHandler(MatchmakingUseCase matchmakingUseCase) {
        this.matchmakingUseCase = matchmakingUseCase;
    }

    @CommandHandler
    public CompletableFuture<String> handle(MatchmakingCommand.EnqueuePlayerCommand command) {
        return matchmakingUseCase.enqueue(command);
    }

    @CommandHandler
    public CompletableFuture<String> handle(MatchmakingCommand.CancelMatchmakingCommand command) {
        return matchmakingUseCase.cancel(command);
    }
}
