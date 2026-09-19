package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection read-only des lobbies.
 * <p>
 * Les lobbies fermés ne sont **pas supprimés** immédiatement : le ticket de matchmaking
 * (`GET /api/matchmaking/queue/{ticketId}`) doit rester lisible pour exposer le `gameId`
 * au client. La purge (agrégat + ligne de projection) survient après rétention, via
 * {@code LobbyPurgedEvent} (piloté par la {@code LobbySaga}).
 */
@Component
public class LobbyProjection {

    private final LobbyRepositoryPort lobbyRepositoryPort;

    public LobbyProjection(LobbyRepositoryPort lobbyRepositoryPort) {
        this.lobbyRepositoryPort = lobbyRepositoryPort;
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyOpenedEvent event) {
        lobbyRepositoryPort.save(
                Lobby.builder()
                        .lobbyId(event.lobbyId())
                        .topicId(event.topicId())
                        .initiatorId(event.initiatorId())
                        .status(LobbyStatus.OPEN)
                        .createdAt(event.openedAt())
                        .updatedAt(event.openedAt())
                        .build()
        );
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        lobbyRepositoryPort.findById(event.lobbyId()).ifPresent(lobby ->
                lobbyRepositoryPort.save(
                        lobby.toBuilder()
                                .challengerId(event.challengerId())
                                .vsBot(LobbyParticipantType.BOT.equals(event.challengerType()))
                                .updatedAt(event.joinedAt())
                                .build()
                )
        );
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        lobbyRepositoryPort.findById(event.lobbyId()).ifPresent(lobby ->
                lobbyRepositoryPort.save(
                        lobby.toBuilder()
                                .status(LobbyStatus.CANCELLED)
                                .updatedAt(event.cancelledAt())
                                .build()
                )
        );
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        lobbyRepositoryPort.findById(event.lobbyId()).ifPresent(lobby ->
                lobbyRepositoryPort.save(
                        lobby.toBuilder()
                                .challengerId(event.challengerId())
                                .gameId(event.gameId())
                                .vsBot(event.vsBot())
                                .status(LobbyStatus.COMPLETED)
                                .updatedAt(event.closedAt())
                                .build()
                )
        );
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        lobbyRepositoryPort.deleteById(event.lobbyId());
    }

}
