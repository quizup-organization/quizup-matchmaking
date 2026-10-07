package io.github.quizup.matchmaking.infrastructure.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.matchmaking.domain.port.out.SessionOutboxPort;
import io.github.quizup.matchmaking.domain.port.out.SessionOutboxPort.OutboxEntry;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Relais de l'outbox du session tier : publie sur le bus Axon (event store + Kafka) les
 * événements écrits par les mutations Redis, sous forme de {@link DomainEventMessage} portant
 * l'identifiant d'agrégat et la séquence — l'historique ({@code GET .../notifications}) et les
 * notifications BFF restent identiques sans event sourcing des agrégats.
 *
 * <p>Livraison au moins une fois : une entrée republiée après crash est un doublon de
 * (agrégat, séquence) détecté dans l'event store, donc acquittée sans republication.</p>
 */
@Component
public class SessionOutboxRelay {

    private static final Logger logger = LoggerFactory.getLogger(SessionOutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final SessionOutboxPort outboxPort;
    private final EventGateway eventGateway;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private volatile boolean recovered;

    public SessionOutboxRelay(SessionOutboxPort outboxPort,
                              EventGateway eventGateway,
                              EventStore eventStore,
                              ObjectMapper objectMapper,
                              @Value("${HOSTNAME:localhost}") String hostname) {
        this.outboxPort = outboxPort;
        this.eventGateway = eventGateway;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    /** Récupère les entrées laissées « en vol » par une incarnation précédente avant de relayer. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInFlight() {
        try {
            outboxPort.recoverInFlight();
        } finally {
            recovered = true;
        }
    }

    @Scheduled(fixedDelayString = "${app.session.outbox-interval-ms:500}")
    public void relay() {
        if (!recovered) {
            return;
        }
        for (int processed = 0; processed < BATCH_SIZE; processed++) {
            Optional<OutboxEntry> next = outboxPort.next();
            if (next.isEmpty()) {
                return;
            }
            OutboxEntry entry = next.get();
            try {
                eventGateway.publish(toDomainEvent(entry));
                outboxPort.acknowledge(entry);
            } catch (Exception exception) {
                if (alreadyStored(entry)) {
                    outboxPort.acknowledge(entry);
                    continue;
                }
                logger.warn("Publication outbox en échec (nouvel essai au prochain tick): {}",
                        entry.entryId(), exception);
                return;
            }
        }
    }

    private DomainEventMessage<?> toDomainEvent(OutboxEntry entry) throws Exception {
        Class<?> payloadType = Class.forName(entry.eventType());
        Object payload = objectMapper.readValue(entry.payload(), payloadType);
        return new GenericDomainEventMessage<>(
                payloadType.getSimpleName(),
                entry.aggregateId(),
                entry.sequence(),
                payload,
                Map.of(),
                UUID.randomUUID().toString(),
                entry.createdAt());
    }

    private boolean alreadyStored(OutboxEntry entry) {
        try {
            return eventStore.readEvents(entry.aggregateId()).asStream()
                    .anyMatch(event -> event.getSequenceNumber() == entry.sequence());
        } catch (Exception exception) {
            return false;
        }
    }
}
