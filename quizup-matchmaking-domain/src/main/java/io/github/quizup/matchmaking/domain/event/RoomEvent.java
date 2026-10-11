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
            boolean vsBot,
            String botDifficulty,
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
     * Les deux joueurs sont présents : la préparation démarre immédiatement
     * ({@code FETCHING_PLAYERS}) ; la partie est créée dès la fin de la préparation
     * ({@link RoomQuestionsLoadedEvent}).
     */
    record RoomAllPlayersPresentEvent(
            String roomId,
            Instant presentAt
    ) implements RoomEvent {
    }

    /**
     * Les profils des joueurs sont résolus : la préparation passe en
     * {@code FETCHING_PLAYED_QUESTIONS} (résolution de l'historique des questions jouées).
     */
    record RoomPlayersLoadedEvent(
            String roomId,
            Instant loadedAt
    ) implements RoomEvent {
    }

    /**
     * Les questions déjà jouées par les joueurs sont résolues : la préparation passe en
     * {@code FETCHING_QUESTIONS} (tirage filtré).
     */
    record RoomPlayedQuestionsLoadedEvent(
            String roomId,
            Instant loadedAt
    ) implements RoomEvent {
    }

    /**
     * Les questions sont tirées : la préparation passe en {@code CREATING_GAME} ; la saga lance
     * la création de la partie (fire-and-forget) et attend l'accusé {@link RoomGameCreatedEvent}.
     */
    record RoomQuestionsLoadedEvent(
            String roomId,
            Instant loadedAt
    ) implements RoomEvent {
    }

    /**
     * La partie a été créée par le service game (accusé de {@code CreateGameCommand}) : la salle
     * passe en {@code GAME_CREATED} et peut être clôturée.
     */
    record RoomGameCreatedEvent(
            String roomId,
            String gameId,
            Instant gameCreatedAt
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
