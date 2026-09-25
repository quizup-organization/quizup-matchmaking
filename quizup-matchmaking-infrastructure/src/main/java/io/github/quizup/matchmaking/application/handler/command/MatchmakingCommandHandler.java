package io.github.quizup.matchmaking.application.handler.command;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.port.in.MatchmakingUseCase;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Expose la file d'attente de matchmaking sur le bus de commandes distribué : le BFF
 * (façade unique) dispatche {@link MatchmakingCommand.EnqueuePlayerCommand} /
 * {@link MatchmakingCommand.CancelMatchmakingCommand} au lieu d'appeler le use case via REST.
 *
 * <p>Les handlers sont <b>synchrones</b> : le {@code SimpleCommandBus} d'Axon ne résout pas un
 * {@code CompletableFuture} retourné par un handler — il le transporte tel quel comme payload
 * (le BFF recevrait alors l'objet {@code CompletableFuture} au lieu du {@code ticketId}). On attend
 * donc explicitement le résultat du use case.</p>
 */
@Component
public class MatchmakingCommandHandler {

    private final MatchmakingUseCase matchmakingUseCase;

    public MatchmakingCommandHandler(MatchmakingUseCase matchmakingUseCase) {
        this.matchmakingUseCase = matchmakingUseCase;
    }

    @CommandHandler
    public String handle(MatchmakingCommand.EnqueuePlayerCommand command) {
        return await(matchmakingUseCase.enqueue(command));
    }

    @CommandHandler
    public String handle(MatchmakingCommand.CancelMatchmakingCommand command) {
        return await(matchmakingUseCase.cancel(command));
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
