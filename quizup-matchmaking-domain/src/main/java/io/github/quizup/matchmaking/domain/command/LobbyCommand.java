package io.github.quizup.matchmaking.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Commandes du salon privé (salle d'attente entre deux humains).
 * L'identifiant de l'agrégat sert de référence de partage : le lien est {@code /join/{lobbyId}}.
 */
public interface LobbyCommand {

    String lobbyId();

    /** Ouvre un salon privé ; le créateur en est le premier participant. */
    record CreateLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String topicId,
            String initiatorId,
            String opponentId
    ) implements LobbyCommand {
    }

    /** Un joueur rejoint le salon (idempotent : rejoindre à nouveau ne fait rien). */
    record JoinLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String playerId
    ) implements LobbyCommand {
    }

    /** Un joueur entre dans la salle (présence temps réel, idempotent). */
    record EnterLobbyRoomCommand(
            @TargetAggregateIdentifier String lobbyId,
            String playerId
    ) implements LobbyCommand {
    }

    /**
     * Commande interne (deadline/présence) : un joueur ne s'est pas présenté, salle close.
     * {@code absentPlayerId} peut être nul : l'agrégat déduit alors l'absent des présences.
     */
    record MissLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String reason,
            String absentPlayerId
    ) implements LobbyCommand {
    }

    /** L'invité refuse un défi nominatif ; le salon est clos. */
    record DeclineLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String playerId
    ) implements LobbyCommand {
    }

    /** Un participant quitte le salon avant la partie : le salon est annulé. */
    record LeaveLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String playerId
    ) implements LobbyCommand {
    }

    /** L'initiateur annule explicitement le salon. */
    record CancelLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String playerId
    ) implements LobbyCommand {
    }

    /** Commande interne (saga) : la partie a été créée ; le salon est purgé immédiatement. */
    record CompleteLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String gameId
    ) implements LobbyCommand {
    }

    /** Commande interne (saga) : la partie n'a pas pu être créée (échec système). */
    record FailLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String reason
    ) implements LobbyCommand {
    }

    /** Commande interne (saga) : salon privé jamais rejoint, expiré. */
    record ExpireLobbyCommand(
            @TargetAggregateIdentifier String lobbyId
    ) implements LobbyCommand {
    }

    /** Commande interne (saga, après rétention) : purge un état terminal. */
    record PurgeLobbyCommand(
            @TargetAggregateIdentifier String lobbyId
    ) implements LobbyCommand {
    }
}
