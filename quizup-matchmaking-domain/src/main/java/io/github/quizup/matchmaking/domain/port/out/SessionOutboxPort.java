package io.github.quizup.matchmaking.domain.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbox des événements du session tier (liste Redis « outbox + inflight ») : les transitions
 * d'état poussent leur événement dans la même opération que la mutation, le relais le publie
 * ensuite sur le bus Axon (event store + Kafka). Réclamation atomique via {@code RPOPLPUSH} ;
 * une entrée en vol non acquittée est récupérée au démarrage (doublon éventuel absorbé par la
 * séquence dans l'event store).
 */
public interface SessionOutboxPort {

    /** Réclame atomiquement la prochaine entrée (déplacée dans la file « en vol »). */
    Optional<OutboxEntry> next();

    /** Acquitte une entrée publiée (ou déjà présente dans l'event store). */
    void acknowledge(OutboxEntry entry);

    /** Récupère les entrées laissées « en vol » par une incarnation précédente. */
    void recoverInFlight();

    /** Entrée d'outbox : type concret (FQCN), agrégat, séquence, payload JSON, date, brut (acquittement). */
    record OutboxEntry(
            String entryId,
            String eventType,
            String aggregateId,
            long sequence,
            String payload,
            Instant createdAt,
            String raw
    ) {
    }
}
