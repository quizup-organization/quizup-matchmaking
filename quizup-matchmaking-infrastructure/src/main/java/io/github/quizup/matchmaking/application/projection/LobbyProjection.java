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
                .opponentId(event.opponentId())
                .status(LobbyStatus.CREATED)
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
    public void on(LobbyEvent.LobbyRoomEnteredEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .initiatorPresent(lobby.initiatorPresent()
                        || event.playerId().equals(lobby.initiatorId()))
                .participantPresent(lobby.participantPresent()
                        || event.playerId().equals(lobby.participantId()))
                .updatedAt(event.enteredAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyAllPlayersPresentEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .allPresentAt(event.presentAt())
                .readyDeadlineAt(event.readyDeadlineAt())
                .updatedAt(event.presentAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyMissedEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.CLOSED)
                .missedReason(event.reason())
                .updatedAt(event.missedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyLeftEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .initiatorPresent(lobby.initiatorPresent()
                        && !event.playerId().equals(lobby.initiatorId()))
                .participantPresent(lobby.participantPresent()
                        && !event.playerId().equals(lobby.participantId()))
                .updatedAt(event.leftAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.CLOSED)
                .updatedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyDeclinedEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.CLOSED)
                .updatedAt(event.declinedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyExpiredEvent event) {
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.CLOSED)
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
        update(event.lobbyId(), lobby -> lobby.toBuilder()
                .status(LobbyStatus.CLOSED)
                .gameId(event.gameId())
                .updatedAt(event.completedAt())
                .build());
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
