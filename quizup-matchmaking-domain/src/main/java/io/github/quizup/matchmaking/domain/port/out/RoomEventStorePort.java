package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;

public interface RoomEventStorePort {

    /** Flux d'événements d'une salle enrichi de leurs métadonnées (historique de notifications). */
    List<EventEnvelope> findEventEnvelopesByRoomId(String roomId);
}
