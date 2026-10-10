package io.github.quizup.matchmaking.infrastructure.out.messaging.adapter;

import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.port.out.RoomEventStorePort;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RoomEventStoreAdapter implements RoomEventStorePort {

    private final EventStore eventStore;

    public RoomEventStoreAdapter(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public List<EventEnvelope> findEventEnvelopesByRoomId(String roomId) {
        List<EventEnvelope> envelopes = new ArrayList<>();
        DomainEventStream eventStream = eventStore.readEvents(roomId);
        while (eventStream.hasNext()) {
            DomainEventMessage<?> message = eventStream.next();
            if (message.getPayload() instanceof RoomEvent roomEvent) {
                envelopes.add(EventEnvelope.of(
                        message.getAggregateIdentifier(),
                        message.getSequenceNumber(),
                        message.getTimestamp(),
                        roomEvent
                ));
            }
        }
        return envelopes;
    }
}
