package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;

import java.util.concurrent.CompletableFuture;

/**
 * Port entrant — file d'attente de matchmaking (appariement par sujet, niveau
 * et pays ; fallback bot géré par la saga du lobby).
 */
public interface MatchmakingUseCase {

    CompletableFuture<String> enqueue(MatchmakingCommand.EnqueuePlayerCommand command);

    CompletableFuture<String> cancel(MatchmakingCommand.CancelMatchmakingCommand command);

    default CompletableFuture<String> enqueue(String playerId, String topicId) {
        return enqueue(new MatchmakingCommand.EnqueuePlayerCommand(playerId, topicId));
    }

    default CompletableFuture<String> cancel(String playerId, String ticketId) {
        return cancel(new MatchmakingCommand.CancelMatchmakingCommand(playerId, ticketId));
    }
}
