package io.github.quizup.matchmaking.application.handler.event;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import io.github.quizup.profile.domain.event.PresenceEvent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Présence en salle : un joueur qui passe hors ligne ferme ses salles encore ouvertes
 * (l'adversaire est libéré immédiatement au lieu d'attendre la fenêtre de 3 min).
 * Les parties déjà lancées ne sont pas concernées (le forfait {@code IN_PROGRESS} vit dans game).
 */
@Component
@ProcessingGroup("room-presence")
public class RoomPresenceHandler {

    private final LobbyRepositoryPort lobbyRepositoryPort;
    private final CommandGateway commandGateway;

    public RoomPresenceHandler(LobbyRepositoryPort lobbyRepositoryPort,
                               CommandGateway commandGateway) {
        this.lobbyRepositoryPort = lobbyRepositoryPort;
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(PresenceEvent.PlayerWentOfflineEvent event) {
        List<Lobby> openRooms = lobbyRepositoryPort.findOpenByPlayerId(event.userId());
        for (Lobby room : openRooms) {
            commandGateway.send(new LobbyCommand.MissLobbyCommand(
                    room.lobbyId(), "PLAYER_OFFLINE", event.userId()));
        }
    }
}
