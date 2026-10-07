package io.github.quizup.matchmaking.application.handler.command;

import io.github.quizup.matchmaking.application.service.LobbyService;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

/**
 * Entrée du bus de commandes pour les salons temps réel : le store chaud Redis porte l'état
 * (plus d'agrégat event-sourcé ni de saga pour les salons éphémères).
 */
@Component
public class LobbyCommandHandler {

    private final LobbyService lobbyService;

    public LobbyCommandHandler(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @CommandHandler
    public void handle(LobbyCommand.CreateLobbyCommand command) {
        lobbyService.create(command);
    }

    @CommandHandler
    public void handle(LobbyCommand.JoinLobbyCommand command) {
        lobbyService.join(command);
    }

    @CommandHandler
    public void handle(LobbyCommand.EnterLobbyRoomCommand command) {
        lobbyService.enter(command);
    }

    @CommandHandler
    public void handle(LobbyCommand.LeaveLobbyCommand command) {
        lobbyService.leave(command);
    }

    @CommandHandler
    public void handle(LobbyCommand.CancelLobbyCommand command) {
        lobbyService.cancel(command);
    }

    @CommandHandler
    public void handle(LobbyCommand.DeclineLobbyCommand command) {
        lobbyService.decline(command);
    }

    /** Commande interne (présence) : un joueur a disparu, la salle est close. */
    @CommandHandler
    public void handle(LobbyCommand.MissLobbyCommand command) {
        if (command.absentPlayerId() == null) {
            return;
        }
        lobbyService.miss(command.lobbyId(), command.absentPlayerId(), command.reason());
    }
}
