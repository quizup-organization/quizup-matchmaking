package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

public interface LobbyDeadline {

    /** Attente d'un adversaire humain avant fallback bot. */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(10);

    String LOBBY_DEADLINE = "lobby-deadline";

    Duration LOBBY_DEADLINE_DURATION = Duration.ofSeconds(15);

    /** Rétention d'un lobby fermé avant purge (laisse le temps au client de lire le ticket). */
    String LOBBY_PURGE = "lobby-purge";

    Duration LOBBY_RETENTION_DURATION = Duration.ofHours(1);
}
