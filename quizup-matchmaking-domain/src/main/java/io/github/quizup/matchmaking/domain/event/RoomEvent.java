package io.github.quizup.matchmaking.domain.event;

import java.time.Instant;

/**
 * Événements de la salle temps réel. L'apparition d'un joueur ({@link RoomEnteredEvent}) porte
 * à la fois la présence et, au premier passage du second humain, l'enregistrement du participant.
 */
public interface RoomEvent {

    String roomId();

    record RoomCreatedEvent(
            String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            Instant expiresAt,
            Instant createdAt
    ) implements RoomEvent {
    }

    /** Un joueur apparaît dans la salle (présence temps réel, idempotent). */
    record RoomEnteredEvent(
            String roomId,
            String playerId,
            Instant enteredAt
    ) implements RoomEvent {
    }

    /**
     * Les deux joueurs sont présents : le compte à rebours de lancement démarre.
     * {@code readyDeadlineAt} borne le créneau de préparation avant création de la partie.
     */
    record RoomAllPlayersPresentEvent(
            String roomId,
            Instant readyDeadlineAt,
            Instant presentAt
    ) implements RoomEvent {
    }

    record RoomLeftEvent(
            String roomId,
            String playerId,
            Instant leftAt
    ) implements RoomEvent {
    }

    record RoomCancelledEvent(
            String roomId,
            String initiatorId,
            String reason,
            Instant cancelledAt
    ) implements RoomEvent {
    }

    /** La partie a été créée depuis la salle (le {@code gameId} est le deep link de l'arène). */
    record RoomCompletedEvent(
            String roomId,
            String gameId,
            Instant completedAt
    ) implements RoomEvent {
    }

    /** La partie n'a pas pu être préparée : les joueurs présents sont informés de l'échec. */
    record RoomFailedEvent(
            String roomId,
            String reason,
            Instant failedAt
    ) implements RoomEvent {
    }

    record RoomExpiredEvent(
            String roomId,
            Instant expiredAt
    ) implements RoomEvent {
    }

    /** Purge après rétention : l'agrégat est supprimé et la projection supprime sa ligne. */
    record RoomPurgedEvent(
            String roomId,
            Instant purgedAt
    ) implements RoomEvent {
    }
}
