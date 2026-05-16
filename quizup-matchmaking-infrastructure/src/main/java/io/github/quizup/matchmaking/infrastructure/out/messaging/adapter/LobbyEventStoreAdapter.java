package io.github.quizup.matchmaking.infrastructure.out.messaging.adapter;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.port.out.LobbyEventStorePort;
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
    public List<LobbyEvent> findAllByLobbyId(String lobbyId) {
        List<LobbyEvent> events = new ArrayList<>();
        DomainEventStream eventStream = eventStore.readEvents(lobbyId);
        while (eventStream.hasNext()) {
            Object payload = eventStream.next().getPayload();
            if (payload instanceof LobbyEvent lobbyEvent) {
                events.add(lobbyEvent);
            }
        }
        return events;
    }
}

