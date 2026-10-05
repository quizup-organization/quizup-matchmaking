package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

public interface LobbyDeadline {

    /** Recherche d'un adversaire humain avant bascule sur une partie bot. */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(5);

    /** Expiration d'un salon privé en attente (lien partagé, adversaire pas encore en salle). */
    String LOBBY_EXPIRY = "lobby-expiry";

    Duration LOBBY_EXPIRY_DURATION = Duration.ofDays(1);

    /** Compte à rebours court avant création de la partie, une fois les deux joueurs présents. */
    String LOBBY_READY_CHECK = "lobby-ready-check";

    Duration LOBBY_READY_CHECK_DURATION = Duration.ofSeconds(3);

    /** Rétention d'un état terminal (annulé/expiré/échoué) avant purge : le client lit l'état. */
    String LOBBY_PURGE = "lobby-purge";

    Duration LOBBY_RETENTION_DURATION = Duration.ofMinutes(2);

    /** Rétention d'un ticket d'appariement terminal avant purge (read model). */
    String MATCHMAKING_PURGE = "matchmaking-purge";

    Duration MATCHMAKING_RETENTION_DURATION = Duration.ofMinutes(2);
}
