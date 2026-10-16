package io.github.quizup.matchmaking.domain.event;

import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Événements de la salle temps réel. L'apparition d'un joueur ({@link RoomEnteredEvent}) porte
 * à la fois la présence, l'enregistrement du second humain et son snapshot de profil préparé par
 * la façade (aucune I/O dans la saga). Les étapes de préparation asynchrones portent leur
 * résultat pour que la saga le mémorise sans relire un read model.
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

    /**
     * Un joueur apparaît dans la salle (présence temps réel, idempotent). {@code player} est
     * {@code null} pour le bot (présence système, aucun profil à résoudre).
     */
    record RoomEnteredEvent(
            String roomId,
            String playerId,
            RoomPlayer player,
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
            Set<String> playedQuestionIds,
            Instant loadedAt
    ) implements RoomEvent {
    }

    /**
     * Les questions sont tirées : la préparation passe en {@code CREATING_GAME} ; la saga lance
     * la création de la partie (fire-and-forget) et attend l'accusé {@link RoomGameCreatedEvent}.
     */
    record RoomQuestionsLoadedEvent(
            String roomId,
            List<DrawnQuestion> questions,
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

    /**
     * Relance (« wizz ») d'un adversaire absent : {@code playerId} demande à {@code targetId} de
     * rejoindre la salle. Événement sans effet de préparation — il porte la cadence
     * (anti-spam par joueur) et alimente le temps réel (push salle, notification cible).
     */
    record RoomWizzedEvent(
            String roomId,
            String playerId,
            String targetId,
            Instant wizzedAt
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
