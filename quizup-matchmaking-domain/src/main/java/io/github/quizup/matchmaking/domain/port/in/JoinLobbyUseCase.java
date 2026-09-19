package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;

import java.util.concurrent.CompletableFuture;

public interface JoinLobbyUseCase {

    CompletableFuture<String> join(LobbyCommand.JoinLobbyCommand command);

    default CompletableFuture<String> join(String lobbyId, String challengerId, LobbyParticipantType challengerType) {
        return join(new LobbyCommand.JoinLobbyCommand(lobbyId, challengerId, challengerType));
    }

    default void joinAndWait(String lobbyId, String challengerId, LobbyParticipantType challengerType) {
        join(lobbyId, challengerId, challengerType).join();
    }
}

