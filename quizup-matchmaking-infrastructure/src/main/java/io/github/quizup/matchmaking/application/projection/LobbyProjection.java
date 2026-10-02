package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection read-only du salon privé. La réussite purge la ligne immédiatement ; les états
 * terminaux (annulé/expiré/échoué) sont conservés le temps de la rétention avant purge.
 */
@Component
@ProcessingGroup("lobby-projection")
public class LobbyProjection {

    private final LobbyRepositoryPort lobbyRepositoryPort;

    public LobbyProjection(LobbyRepositoryPort lobbyRepositoryPort) {
        this.lobbyRepositoryPort = lobbyRepositoryPort;
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCreatedEvent event) {
        lobbyRepositoryPort.save(Lobby.builder()
                .lobbyId(event.lobbyId())
                .topicId(event.topicId())
                .initiatorId(event.initiatorId())
                .status(LobbyStatus.OPEN)
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .updatedAt(event.createdAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .participantId(event.participantId())
                .updatedAt(event.joinedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.CANCELLED)
                .updatedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyExpiredEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.EXPIRED)
                .updatedAt(event.expiredAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyFailedEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.FAILED)
                .updatedAt(event.failedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        lobbyRepositoryPort.deleteById(event.lobbyId());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        lobbyRepositoryPort.deleteById(event.lobbyId());
    }

    private void update(String lobbyId, java.util.function.UnaryOperator<Lobby> transform) {
        lobbyRepositoryPort.findById(lobbyId).ifPresent(lobby ->
                lobbyRepositoryPort.save(transform.apply(lobby)));
    }
}
