package io.github.quizup.matchmaking.infrastructure.out.messaging.adapter;

import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingEventStorePort;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MatchmakingEventStoreAdapter implements MatchmakingEventStorePort {

    private final EventStore eventStore;

    public MatchmakingEventStoreAdapter(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public List<EventEnvelope> findEventEnvelopesByMatchmakingId(String matchmakingId) {
        List<EventEnvelope> envelopes = new ArrayList<>();
        DomainEventStream eventStream = eventStore.readEvents(matchmakingId);
        while (eventStream.hasNext()) {
            DomainEventMessage<?> message = eventStream.next();
            if (message.getPayload() instanceof MatchmakingEvent event) {
                envelopes.add(EventEnvelope.of(
                        message.getAggregateIdentifier(),
                        message.getSequenceNumber(),
                        message.getTimestamp(),
                        event));
            }
        }
        return envelopes;
    }
}
