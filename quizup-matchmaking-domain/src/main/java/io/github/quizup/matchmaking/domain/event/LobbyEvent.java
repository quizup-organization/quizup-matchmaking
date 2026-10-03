package io.github.quizup.matchmaking.domain.event;

import java.time.Instant;

public interface LobbyEvent {

    String lobbyId();

    record LobbyCreatedEvent(
            String lobbyId,
            String topicId,
            String initiatorId,
            String opponentId,
            Instant expiresAt,
            Instant createdAt
    ) implements LobbyEvent {
    }

    record LobbyJoinedEvent(
            String lobbyId,
            String participantId,
            Instant joinedAt
    ) implements LobbyEvent {
    }

    /** Défi nominatif refusé par l'invité ; le salon est clos. */
    record LobbyDeclinedEvent(
            String lobbyId,
            String initiatorId,
            String opponentId,
            Instant declinedAt
    ) implements LobbyEvent {
    }

    /** La partie a été créée ; le salon est purgé immédiatement (aucun statut persistant). */
    record LobbyCompletedEvent(
            String lobbyId,
            String gameId,
            Instant completedAt
    ) implements LobbyEvent {
    }

    record LobbyLeftEvent(
            String lobbyId,
            String playerId,
            Instant leftAt
    ) implements LobbyEvent {
    }

    record LobbyCancelledEvent(
            String lobbyId,
            String initiatorId,
            String reason,
            Instant cancelledAt
    ) implements LobbyEvent {
    }

    /** La partie n'a pas pu être créée (échec système). */
    record LobbyFailedEvent(
            String lobbyId,
            String reason,
            Instant failedAt
    ) implements LobbyEvent {
    }

    record LobbyExpiredEvent(
            String lobbyId,
            Instant expiredAt
    ) implements LobbyEvent {
    }

    /** Purge après rétention : l'agrégat est supprimé et la projection supprime sa ligne. */
    record LobbyPurgedEvent(
            String lobbyId,
            Instant purgedAt
    ) implements LobbyEvent {
    }
}
