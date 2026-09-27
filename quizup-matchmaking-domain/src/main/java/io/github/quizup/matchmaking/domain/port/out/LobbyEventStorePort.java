package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;

public interface LobbyEventStorePort {

    /** Flux d'événements d'un lobby enrichi de leurs métadonnées (historique de notifications). */
    List<EventEnvelope> findEventEnvelopesByLobbyId(String lobbyId);
}
