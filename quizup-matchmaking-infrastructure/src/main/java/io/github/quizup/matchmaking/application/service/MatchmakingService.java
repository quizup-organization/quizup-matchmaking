package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.MatchmakingRules;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.in.CancelLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetOpenLobbiesByTopicUseCase;
import io.github.quizup.matchmaking.domain.port.in.JoinLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.MatchmakingUseCase;
import io.github.quizup.matchmaking.domain.port.in.OpenLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service applicatif de la file d'attente : appariement par sujet, niveau (±5)
 * et préférence pays. Si aucun adversaire compatible n'est trouvé, un lobby est
 * ouvert — la {@code LobbySaga} gère le fallback bot après expiration.
 */
@Service
public class MatchmakingService implements MatchmakingUseCase {

    private final GetOpenLobbiesByTopicUseCase getOpenLobbiesByTopicUseCase;
    private final OpenLobbyUseCase openLobbyUseCase;
    private final JoinLobbyUseCase joinLobbyUseCase;
    private final CancelLobbyUseCase cancelLobbyUseCase;
    private final MatchmakingPlayerPort matchmakingPlayerPort;

    public MatchmakingService(GetOpenLobbiesByTopicUseCase getOpenLobbiesByTopicUseCase,
                              OpenLobbyUseCase openLobbyUseCase,
                              JoinLobbyUseCase joinLobbyUseCase,
                              CancelLobbyUseCase cancelLobbyUseCase,
                              MatchmakingPlayerPort matchmakingPlayerPort) {
        this.getOpenLobbiesByTopicUseCase = getOpenLobbiesByTopicUseCase;
        this.openLobbyUseCase = openLobbyUseCase;
        this.joinLobbyUseCase = joinLobbyUseCase;
        this.cancelLobbyUseCase = cancelLobbyUseCase;
        this.matchmakingPlayerPort = matchmakingPlayerPort;
    }

    @Override
    public CompletableFuture<String> enqueue(MatchmakingCommand.EnqueuePlayerCommand command) {
        PlayerSummary player = matchmakingPlayerPort.getPlayer(command.playerId());

        return getOpenLobbiesByTopicUseCase.getOpenByTopicId(command.topicId())
                .thenCompose(lobbies -> {
                    Optional<Lobby> match = selectBest(lobbies, command.playerId(), player);

                    if (match.isPresent()) {
                        String lobbyId = match.get().lobbyId();
                        return joinLobbyUseCase
                                .join(lobbyId, command.playerId(), LobbyParticipantType.HUMAN)
                                .thenApply(_ -> lobbyId);
                    }

                    String lobbyId = UUID.randomUUID().toString();
                    return openLobbyUseCase
                            .open(new LobbyCommand.OpenLobbyCommand(lobbyId, command.playerId(), command.topicId()))
                            .thenApply(_ -> lobbyId);
                });
    }

    @Override
    public CompletableFuture<String> cancel(MatchmakingCommand.CancelMatchmakingCommand command) {
        return cancelLobbyUseCase.cancel(command.ticketId(), command.playerId());
    }

    private Optional<Lobby> selectBest(List<Lobby> lobbies, String playerId, PlayerSummary player) {
        return lobbies.stream()
                .filter(lobby -> !playerId.equals(lobby.initiatorId()))
                .map(lobby -> Map.entry(lobby, matchmakingPlayerPort.getPlayer(lobby.initiatorId())))
                .filter(entry -> Math.abs(entry.getValue().level() - player.level()) <= MatchmakingRules.LEVEL_WINDOW)
                .sorted(Comparator
                        .comparing((Map.Entry<Lobby, PlayerSummary> entry) ->
                                !Objects.equals(entry.getValue().country(), player.country()))
                        .thenComparingInt(entry ->
                                Math.abs(entry.getValue().level() - player.level()))
                        .thenComparing(entry -> entry.getKey().createdAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
