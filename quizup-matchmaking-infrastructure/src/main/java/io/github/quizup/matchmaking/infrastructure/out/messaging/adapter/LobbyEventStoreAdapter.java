package io.github.quizup.matchmaking.infrastructure.out.messaging.adapter;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.port.out.LobbyEventStorePort;
import io.github.quizup.microservice.core.domain.model.notification.NotificationEnvelope;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LobbyEventStoreAdapter implements LobbyEventStorePort {

    private final EventStore eventStore;

    public LobbyEventStoreAdapter(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public List<NotificationEnvelope<LobbyEvent>> findEventEnvelopesByLobbyId(String lobbyId) {
        List<NotificationEnvelope<LobbyEvent>> envelopes = new ArrayList<>();
        DomainEventStream eventStream = eventStore.readEvents(lobbyId);
        while (eventStream.hasNext()) {
            DomainEventMessage<?> message = eventStream.next();
            if (message.getPayload() instanceof LobbyEvent lobbyEvent) {
                envelopes.add(new NotificationEnvelope<>(
                        message.getIdentifier(),
                        message.getAggregateIdentifier(),
                        message.getSequenceNumber(),
                        message.getTimestamp(),
                        lobbyEvent
                ));
            }
        }
        return envelopes;
    }
}
