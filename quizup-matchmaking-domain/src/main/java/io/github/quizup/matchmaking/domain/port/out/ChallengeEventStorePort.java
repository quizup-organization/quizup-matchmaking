package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;

public interface ChallengeEventStorePort {

    /** Flux d'événements d'un défi enrichi de leurs métadonnées (historique de notifications). */
    List<EventEnvelope> findEventEnvelopesByChallengeId(String challengeId);
}
