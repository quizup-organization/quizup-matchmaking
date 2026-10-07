package io.github.quizup.matchmaking.application.handler.command;

import io.github.quizup.matchmaking.application.service.MatchmakingService;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

/**
 * Entrée du bus de commandes pour l'appariement public : le store chaud Redis porte l'état
 * (plus d'agrégat event-sourcé ni de saga pour les tickets éphémères).
 */
@Component
public class MatchmakingCommandHandler {

    private final MatchmakingService matchmakingService;

    public MatchmakingCommandHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @CommandHandler
    public void handle(MatchmakingCommand.CreateMatchmakingCommand command) {
        matchmakingService.create(command);
    }

    @CommandHandler
    public void handle(MatchmakingCommand.CancelMatchmakingCommand command) {
        matchmakingService.cancel(command);
    }
}
