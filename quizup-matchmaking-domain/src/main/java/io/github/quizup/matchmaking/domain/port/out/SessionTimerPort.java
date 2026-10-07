package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.SessionTimer;

import java.time.Instant;
import java.util.List;

/**
 * Timers du session tier (ZSET Redis) : échéances durables, distribuées et idempotentes.
 * Un worker réclame les échéances dépassées ; chaque timer n'est tiré que par une instance.
 */
public interface SessionTimerPort {

    /** Programme (ou reprogramme) l'échéance d'un type pour une référence donnée. */
    void schedule(String type, String referenceId, Instant fireAt);

    /** Réclame atomiquement les échéances dépassées (retirées du ZSET avant dispatch). */
    List<SessionTimer.Due> claimDue(Instant now, int limit);
}
