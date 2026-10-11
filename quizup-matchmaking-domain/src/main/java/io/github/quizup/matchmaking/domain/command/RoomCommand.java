package io.github.quizup.matchmaking.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Commandes de la salle : deux humains (salle partagée ou appariement) ou un humain contre le
 * bot. L'identifiant de l'agrégat sert de référence de partage : le lien est {@code /join/{roomId}}.
 */
public interface RoomCommand {

    String roomId();

    /**
     * Ouvre une salle ; le créateur en est l'initiateur. Nominatif si {@code opponentId} est
     * renseigné (défi, appariement) ; {@code vsBot} marque une salle dont le second joueur est le
     * bot (join automatique par la saga). {@code botDifficulty} est le nom de l'enum game
     * (nullable, défaut NORMAL) — porté en {@code String} pour que le domaine matchmaking ne
     * dépende jamais du domaine game.
     */
    record CreateRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            boolean vsBot,
            String botDifficulty
    ) implements RoomCommand {
    }

    /**
     * Le joueur <b>apparaît</b> dans la salle : présence temps réel et, pour le second humain,
     * enregistrement implicite comme participant. Commande unique — le client l'émet quand
     * l'utilisateur est réellement dans la salle ; aucune saga ne le fait à sa place (seule
     * exception : le join automatique du bot sur une salle {@code vsBot}).
     */
    record JoinRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /**
     * Sortie <b>non destructive</b> : le joueur quitte la salle mais reste participant ; il peut
     * revenir jusqu'à l'expiration. Autorisée uniquement tant que les deux joueurs ne sont pas
     * présents ({@code WAITING_PLAYERS}) : une préparation engagée ne peut plus être quittée.
     */
    record LeaveRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /** L'initiateur annule explicitement la salle (possible tant que la salle n'est pas terminale). */
    record CancelRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : les profils des joueurs sont résolus. */
    record MarkRoomPlayersLoadedCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : les questions sont tirées, la création de la partie peut démarrer. */
    record MarkRoomQuestionsLoadedCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : la partie a été créée ; la salle reste lisible jusqu'à sa purge. */
    record CompleteRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String gameId
    ) implements RoomCommand {
    }

    /**
     * Commande interne (saga) : la partie n'a pas pu être préparée (questions insuffisantes,
     * création impossible). Le joueur présent est informé par {@code RoomFailedEvent}.
     */
    record FailRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String reason
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : salle jamais lancée, expirée. */
    record ExpireRoomCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }

    /** Commande interne (saga, après rétention) : purge un état terminal. */
    record PurgeRoomCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }
}
