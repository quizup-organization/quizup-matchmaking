package io.github.quizup.matchmaking.infrastructure.out.messaging;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.infrastructure.out.messaging.mapper.LobbyEventNotificationMapper;
import io.github.quizup.matchmaking.infrastructure.out.messaging.response.LobbyNotification;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LobbyNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(LobbyNotificationService.class);
    private static final String DESTINATION_PREFIX = "/topic/lobbies/";
    private final SimpMessagingTemplate messagingTemplate;

    public LobbyNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventHandler
    public void onLobbyEvent(LobbyEvent event) {
        LobbyEventNotificationMapper.toNotification(event)
                .ifPresentOrElse(
                        notification -> send(event.lobbyId(), notification),
                        () -> logger.warn("Aucun mapping de notification pour l'événement: {}", event.getClass().getSimpleName()));
    }

    private void send(String lobbyId, LobbyNotification payload) {
        logger.info("{} publié: lobbyId={}", payload.type(), payload.lobbyId());
        messagingTemplate.convertAndSend(DESTINATION_PREFIX + lobbyId, payload);
    }
}
