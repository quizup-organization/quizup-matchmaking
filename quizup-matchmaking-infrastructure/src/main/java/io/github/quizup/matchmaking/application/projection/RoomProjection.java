package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.Room;
import io.github.quizup.matchmaking.domain.model.RoomStatus;
import io.github.quizup.matchmaking.domain.port.out.RoomRepositoryPort;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/**
 * Projection read-only de la salle. Tous les états terminaux (partie créée, annulée, expirée,
 * échouée) sont conservés le temps de la rétention, puis supprimés sur {@code RoomPurgedEvent}.
 */
@Component
@ProcessingGroup("room-projection")
public class RoomProjection {

    private final RoomRepositoryPort roomRepositoryPort;

    public RoomProjection(RoomRepositoryPort roomRepositoryPort) {
        this.roomRepositoryPort = roomRepositoryPort;
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomCreatedEvent event) {
        roomRepositoryPort.save(Room.builder()
                .roomId(event.roomId())
                .topicId(event.topicId())
                .initiatorId(event.initiatorId())
                .opponentId(event.opponentId())
                .vsBot(event.vsBot())
                .status(RoomStatus.WAITING_PLAYERS)
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .updatedAt(event.createdAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomEnteredEvent event) {
        update(event.roomId(), room -> {
            boolean isInitiator = event.playerId().equals(room.initiatorId());
            String participantId = !isInitiator && room.participantId() == null
                    ? event.playerId()
                    : room.participantId();
            return room.toBuilder()
                    .participantId(participantId)
                    .initiatorPresent(room.initiatorPresent() || isInitiator)
                    .participantPresent(room.participantPresent()
                            || (!isInitiator && event.playerId().equals(participantId)))
                    .updatedAt(event.enteredAt())
                    .build();
        });
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FETCHING_PLAYERS)
                .updatedAt(event.presentAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomPlayersLoadedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FETCHING_QUESTIONS)
                .updatedAt(event.loadedAt())
                .build());
    }

    /** Fin de préparation : la saga lance la création de la partie. */
    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomQuestionsLoadedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.CREATING_GAME)
                .updatedAt(event.loadedAt())
                .build());
    }

    /** Le service game a accusé la création : le {@code gameId} est exposé avant la clôture. */
    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomGameCreatedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.GAME_CREATED)
                .gameId(event.gameId())
                .updatedAt(event.gameCreatedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomLeftEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .initiatorPresent(room.initiatorPresent()
                        && !event.playerId().equals(room.initiatorId()))
                .participantPresent(room.participantPresent()
                        && !event.playerId().equals(room.participantId()))
                .updatedAt(event.leftAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomCancelledEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.CANCELLED)
                .updatedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomCompletedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.COMPLETED)
                .gameId(event.gameId())
                .updatedAt(event.completedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomFailedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FAILED)
                .updatedAt(event.failedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomExpiredEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.EXPIRED)
                .updatedAt(event.expiredAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomPurgedEvent event) {
        roomRepositoryPort.deleteById(event.roomId());
    }

    private void update(String roomId, UnaryOperator<Room> transform) {
        roomRepositoryPort.findById(roomId).ifPresent(room ->
                roomRepositoryPort.save(transform.apply(room)));
    }
}
