package io.github.quizup.matchmaking.domain.command;

import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public interface LobbyCommand {

    /**
     * Un joueur ouvre un lobby pour trouver un adversaire.
     * L'initiateur est inclus — un lobby vide n'existe pas.
     */
    record OpenLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String initiatorId,
            String topicId
    ) implements LobbyCommand {}

    /**
     * Un challenger rejoint un lobby ouvert.
     * Émis par le Controller (humain) ou la Saga (bot).
     */
    record JoinLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String challengerId,
            LobbyParticipantType challengerType
    ) implements LobbyCommand {}

    /**
     * L'initiateur ferme sa recherche sans avoir trouvé d'adversaire.
     */
    record CancelLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String initiatorId
    ) implements LobbyCommand {}

    /**
     * Commande interne (Saga) : la partie est créée, le lobby se ferme.
     */
    record CompleteLobbyCommand(
            @TargetAggregateIdentifier String lobbyId,
            String gameId
    ) implements LobbyCommand {}

    record ExpireLobbyCommand(
            @TargetAggregateIdentifier String lobbyId
    ) implements LobbyCommand {}
}
