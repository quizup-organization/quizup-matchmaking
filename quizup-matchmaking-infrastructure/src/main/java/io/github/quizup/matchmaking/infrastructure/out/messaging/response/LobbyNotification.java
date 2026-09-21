package io.github.quizup.matchmaking.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notifications de lobby. Le discriminant est exposé explicitement par {@code type}
 * ({@link JsonProperty}) : sérialisé en REST **et** en WebSocket.
 */
public interface LobbyNotification {

    @JsonProperty("type")
    LobbyNotificationType type();

    String lobbyId();

    enum LobbyNotificationType {
        OPENED,
        JOINED,
        COMPLETED,
        CANCELLED
    }

    record LobbyOpenedNotification(
            String lobbyId,
            String topicId,
            String initiatorId
    ) implements LobbyNotification {

        @Override
        public LobbyNotificationType type() {
            return LobbyNotificationType.OPENED;
        }
    }

    record LobbyJoinedNotification(
            String lobbyId,
            String challengerId,
            boolean vsBot
    ) implements LobbyNotification {

        @Override
        public LobbyNotificationType type() {
            return LobbyNotificationType.JOINED;
        }
    }

    record LobbyCompletedNotification(
            String lobbyId,
            String gameId,
            boolean vsBot
    ) implements LobbyNotification {

        @Override
        public LobbyNotificationType type() {
            return LobbyNotificationType.COMPLETED;
        }
    }

    record LobbyCancelledNotification(
            String lobbyId
    ) implements LobbyNotification {

        @Override
        public LobbyNotificationType type() {
            return LobbyNotificationType.CANCELLED;
        }
    }
}