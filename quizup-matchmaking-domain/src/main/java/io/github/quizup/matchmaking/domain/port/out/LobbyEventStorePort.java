package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.microservice.core.domain.model.notification.NotificationEnvelope;

import java.util.List;

public interface LobbyEventStorePort {

    /** Flux d'événements d'un lobby enrichi de leurs métadonnées (historique de notifications). */
    List<NotificationEnvelope<LobbyEvent>> findEventEnvelopesByLobbyId(String lobbyId);
}
