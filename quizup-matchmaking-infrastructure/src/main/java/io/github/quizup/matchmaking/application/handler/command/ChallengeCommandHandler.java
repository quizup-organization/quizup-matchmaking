package io.github.quizup.matchmaking.application.handler.command;

import io.github.quizup.matchmaking.application.service.ChallengeService;
import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

/**
 * Entrée du bus de commandes pour les défis nominatifs : le store chaud Redis porte l'état
 * (plus d'agrégat event-sourcé ni de saga de défi).
 */
@Component
public class ChallengeCommandHandler {

    private final ChallengeService challengeService;

    public ChallengeCommandHandler(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @CommandHandler
    public void handle(ChallengeCommand.CreateChallengeCommand command) {
        challengeService.create(command);
    }

    @CommandHandler
    public void handle(ChallengeCommand.AcceptChallengeCommand command) {
        challengeService.accept(command);
    }

    @CommandHandler
    public void handle(ChallengeCommand.DeclineChallengeCommand command) {
        challengeService.decline(command);
    }

    @CommandHandler
    public void handle(ChallengeCommand.CancelChallengeCommand command) {
        challengeService.cancel(command);
    }
}
