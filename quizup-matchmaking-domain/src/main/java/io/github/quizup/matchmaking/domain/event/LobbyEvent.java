package io.github.quizup.matchmaking.domain.event;

import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;

import java.time.Instant;

public interface LobbyEvent {

    String lobbyId();

    record LobbyOpenedEvent(
            String lobbyId,
            String topicId,
            String initiatorId,
            Instant openedAt
    ) implements LobbyEvent {}

    record LobbyJoinedEvent(
            String lobbyId,
            String challengerId,
            LobbyParticipantType challengerType,
            Instant joinedAt
    ) implements LobbyEvent {}

    record LobbyCancelledEvent(
            String lobbyId,
            String initiatorId,
            Instant cancelledAt
    ) implements LobbyEvent {}

    record LobbyCompletedEvent(
            String lobbyId,
            String gameId,
            String initiatorId,
            String challengerId,
            String topicId,
            boolean vsBot,
            Instant closedAt
    ) implements LobbyEvent {}

    /**
     * Le lobby est purgé après rétention : l'agrégat est supprimé et la projection
     * supprime sa ligne. Émis par {@code PurgeLobbyCommand}.
     */
    record LobbyPurgedEvent(
            String lobbyId,
            Instant purgedAt
    ) implements LobbyEvent {}
}
