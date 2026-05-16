package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        lobbyRepositoryPort.deleteById(event.lobbyId());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        lobbyRepositoryPort.deleteById(event.lobbyId());
    }

}
