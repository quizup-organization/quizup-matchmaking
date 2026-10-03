package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

public interface LobbyDeadline {

    /** Recherche d'un adversaire humain avant bascule sur une partie bot. */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(5);

    /** Expiration d'un salon privé jamais rejoint. */
    String LOBBY_EXPIRY = "lobby-expiry";

    Duration LOBBY_EXPIRY_DURATION = Duration.ofHours(1);

    /** Rétention d'un état terminal (annulé/expiré/échoué) avant purge : le client lit l'état. */
    String LOBBY_PURGE = "lobby-purge";

    Duration LOBBY_RETENTION_DURATION = Duration.ofMinutes(2);

    /** Rétention d'un ticket d'appariement terminal avant purge (read model). */
    String MATCHMAKING_PURGE = "matchmaking-purge";

    Duration MATCHMAKING_RETENTION_DURATION = Duration.ofMinutes(2);
}
