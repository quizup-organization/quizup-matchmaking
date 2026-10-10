package io.github.quizup.matchmaking.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Commandes de la salle (deux humains). L'identifiant de l'agrégat sert de référence de
 * partage : le lien est {@code /join/{roomId}}.
 */
public interface RoomCommand {

    String roomId();

    /** Ouvre une salle ; le créateur en est l'initiateur. Nominatif si {@code opponentId} renseigné. */
    record CreateRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String topicId,
            String initiatorId,
            String opponentId
    ) implements RoomCommand {
    }

    /**
     * Le joueur <b>apparaît</b> dans la salle : présence temps réel et, pour le second humain,
     * enregistrement implicite comme participant. Commande unique — le client l'émet quand
     * l'utilisateur est réellement dans la salle ; aucune saga ne le fait à sa place.
     */
    record JoinRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /**
     * Sortie <b>non destructive</b> : le joueur quitte la salle mais reste participant ; il peut
     * revenir jusqu'à l'expiration.
     */
    record LeaveRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /** L'initiateur annule explicitement la salle. */
    record CancelRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
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
