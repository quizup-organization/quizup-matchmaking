package io.github.quizup.matchmaking.infrastructure.out.messaging.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LobbyNotification.LobbyOpenedNotification.class,    name = "OPENED"),
        @JsonSubTypes.Type(value = LobbyNotification.LobbyJoinedNotification.class,    name = "JOINED"),
        @JsonSubTypes.Type(value = LobbyNotification.LobbyCompletedNotification.class, name = "COMPLETED"),
        @JsonSubTypes.Type(value = LobbyNotification.LobbyCancelledNotification.class, name = "CANCELLED"),
        @JsonSubTypes.Type(value = LobbyNotification.LobbyExpiredNotification.class,   name = "EXPIRED")
})
public interface LobbyNotification {

    LobbyNotificationType type();

    String lobbyId();

    enum LobbyNotificationType {
        OPENED,
        JOINED,
        COMPLETED,
        CANCELLED,
        EXPIRED
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

    record LobbyExpiredNotification(
            String lobbyId
    ) implements LobbyNotification {

        @Override
        public LobbyNotificationType type() {
            return LobbyNotificationType.EXPIRED;
        }
    }
}