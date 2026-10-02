package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;

public interface MatchmakingEventStorePort {

    List<EventEnvelope> findEventEnvelopesByMatchmakingId(String matchmakingId);
}
