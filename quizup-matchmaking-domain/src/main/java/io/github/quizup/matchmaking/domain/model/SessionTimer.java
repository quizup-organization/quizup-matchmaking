package io.github.quizup.matchmaking.domain.model;

/**
 * Types de timers du session tier (ZSET {@code session:timers}, un worker les réclame à échéance).
 * Les timers ne portent qu'un type et une référence : l'état est relu dans le store au tir
 * (un timer devenu obsolète est un no-op).
 */
public final class SessionTimer {

    /** Échéance d'appariement public : bascule sur une partie bot si toujours en recherche. */
    public static final String MATCHMAKING_DEADLINE = "MATCHMAKING_DEADLINE";

    /** Fin du compte à rebours de lancement d'un salon (les deux joueurs présents). */
    public static final String LOBBY_READY_CHECK = "LOBBY_READY_CHECK";

    /** Expiration d'un salon jamais lancé. */
    public static final String LOBBY_EXPIRY = "LOBBY_EXPIRY";

    /** Expiration d'un défi nominatif sans réponse. */
    public static final String CHALLENGE_EXPIRY = "CHALLENGE_EXPIRY";

    private SessionTimer() {
    }

    /** Timer réclamé par le worker, prêt à être dispatché. */
    public record Due(String type, String referenceId) {
    }
}
