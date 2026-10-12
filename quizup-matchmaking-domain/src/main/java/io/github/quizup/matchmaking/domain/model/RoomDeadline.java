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

    /**
     * Accusé de création de la partie ({@code GameCreatedEvent}) attendu par la saga : la
     * commande est fire-and-forget, donc un accusé manquant (échec/routage perdu) fait échouer
     * la salle au lieu de la laisser bloquée en {@code CREATING_GAME}.
     */
    String ROOM_GAME_CREATION = "room-game-creation";

    Duration ROOM_GAME_CREATION_DURATION = Duration.ofSeconds(30);

    /**
     * Borne d'une étape de préparation asynchrone (profils déjà portés par les événements ;
     * historique des questions et tirage résolus via des requêtes non bloquantes) : une réponse
     * perdue fait échouer la salle au lieu de la laisser bloquée en {@code FETCHING_*}.
     */
    Duration ROOM_PREPARATION_STEP_TIMEOUT = Duration.ofSeconds(10);

    /** Rétention d'un état terminal (annulé/expiré/échoué) avant purge : le client lit l'état. */
    String ROOM_PURGE = "room-purge";

    Duration ROOM_RETENTION_DURATION = Duration.ofMinutes(2);
}
