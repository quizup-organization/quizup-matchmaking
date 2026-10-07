package io.github.quizup.matchmaking.application.handler.event;

import io.github.quizup.matchmaking.application.service.LobbyService;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.out.LobbyStorePort;
import io.github.quizup.profile.domain.event.PresenceEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Présence en salle : un joueur qui passe hors ligne ferme ses salles encore ouvertes
 * (l'adversaire est libéré immédiatement au lieu d'attendre l'expiration du salon).
 * Les parties déjà lancées ne sont pas concernées (le forfait {@code IN_PROGRESS} vit dans game).
 */
@Component
@ProcessingGroup("room-presence")
public class RoomPresenceHandler {

    private final LobbyStorePort lobbyStorePort;
    private final LobbyService lobbyService;

    public RoomPresenceHandler(LobbyStorePort lobbyStorePort, LobbyService lobbyService) {
        this.lobbyStorePort = lobbyStorePort;
        this.lobbyService = lobbyService;
    }

    @EventHandler
    public void on(PresenceEvent.PlayerWentOfflineEvent event) {
        List<Lobby> openRooms = lobbyStorePort.findOpenByPlayerId(event.userId());
        for (Lobby room : openRooms) {
            lobbyService.miss(room.lobbyId(), event.userId(), "PLAYER_OFFLINE");
        }
    }
}
