package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines de la salle (cycle de vie temps réel). Les deadlines de l'appariement public vivent
 * dans {@link MatchmakingDeadline}.
 */
public interface RoomDeadline {

    /** Expiration d'une salle en attente (lien partagé, adversaire pas encore en salle). */
    String ROOM_EXPIRY = "room-expiry";

    Duration ROOM_EXPIRY_DURATION = Duration.ofDays(1);

    /** Compte à rebours court de préparation avant création de la partie, une fois les deux présents. */
    String ROOM_READY_CHECK = "room-ready-check";

    Duration ROOM_READY_CHECK_DURATION = Duration.ofSeconds(3);

    /** Rétention d'un état terminal (annulé/expiré/échoué) avant purge : le client lit l'état. */
    String ROOM_PURGE = "room-purge";

    Duration ROOM_RETENTION_DURATION = Duration.ofMinutes(2);
}
