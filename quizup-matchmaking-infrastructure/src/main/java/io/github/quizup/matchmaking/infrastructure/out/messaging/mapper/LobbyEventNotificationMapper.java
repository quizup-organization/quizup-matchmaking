package io.github.quizup.matchmaking.infrastructure.out.messaging.mapper;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.infrastructure.out.messaging.response.LobbyNotification;

import java.util.Optional;

import static java.util.Objects.isNull;

public final class LobbyEventNotificationMapper {

    private LobbyEventNotificationMapper() {
    }

    public static Optional<LobbyNotification> toNotification(LobbyEvent event) {
        if (isNull(event)) {
            return Optional.empty();
        }

        return switch (event) {
            case LobbyEvent.LobbyOpenedEvent lobbyOpenedEvent -> Optional.of(
                    new LobbyNotification.LobbyOpenedNotification(
                            lobbyOpenedEvent.lobbyId(),
                            lobbyOpenedEvent.topicId(),
                            lobbyOpenedEvent.initiatorId()
                    )
            );

            case LobbyEvent.LobbyJoinedEvent lobbyJoinedEvent -> Optional.of(
                    new LobbyNotification.LobbyJoinedNotification(
                            lobbyJoinedEvent.lobbyId(),
                            lobbyJoinedEvent.challengerId(),
                            LobbyParticipantType.BOT.equals(lobbyJoinedEvent.challengerType())
                    )
            );

            case LobbyEvent.LobbyCompletedEvent lobbyCompletedEvent -> Optional.of(
                    new LobbyNotification.LobbyCompletedNotification(
                            lobbyCompletedEvent.lobbyId(),
                            lobbyCompletedEvent.gameId(),
                            lobbyCompletedEvent.vsBot()
                    )
            );

            case LobbyEvent.LobbyCancelledEvent lobbyCancelledEvent -> Optional.of(
                    new LobbyNotification.LobbyCancelledNotification(
                            lobbyCancelledEvent.lobbyId()
                    )
            );

            case LobbyEvent.LobbyExpiredEvent lobbyExpiredEvent -> Optional.of(
                    new LobbyNotification.LobbyExpiredNotification(
                            lobbyExpiredEvent.lobbyId()
                    )
            );

            default -> Optional.empty();
        };
    }
}
