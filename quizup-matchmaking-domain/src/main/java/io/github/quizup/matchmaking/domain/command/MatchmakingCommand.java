package io.github.quizup.matchmaking.domain.command;

public interface MatchmakingCommand {

    /**
     * Met un joueur en file d'attente pour un sujet.
     */
    record EnqueuePlayerCommand(
            String playerId,
            String topicId
    ) implements MatchmakingCommand {
    }

    /**
     * Annule la recherche d'un joueur (ticket = lobby).
     */
    record CancelMatchmakingCommand(
            String playerId,
            String ticketId
    ) implements MatchmakingCommand {
    }
}
