package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
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
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Service applicatif de la file d'attente : appariement par sujet, niveau (±5), préférence pays,
 * **présence** (on ne rejoint jamais un lobby dont l'initiateur est hors ligne) et
 * **compatibilité linguistique** (le thème doit couvrir les langues des deux joueurs — sélection
 * stricte côté game). Si aucun adversaire compatible n'est trouvé, un lobby est ouvert — la
 * {@code LobbySaga} gère le fallback bot automatique après expiration, ou annule si l'initiateur
 * a disparu.
 *
 * <p>Race de join : entre la sélection et l'envoi de la commande, un lobby peut être annulé ou
 * rempli ; la commande est alors rejouée sur le candidat suivant (borné).</p>
 */
@Service
public class MatchmakingService implements MatchmakingUseCase {

    private static final int MAX_JOIN_ATTEMPTS = 3;

    private final GetOpenLobbiesByTopicUseCase getOpenLobbiesByTopicUseCase;
    private final OpenLobbyUseCase openLobbyUseCase;
    private final JoinLobbyUseCase joinLobbyUseCase;
    private final CancelLobbyUseCase cancelLobbyUseCase;
    private final MatchmakingPlayerPort matchmakingPlayerPort;
    private final TopicAvailabilityPort topicAvailabilityPort;

    public MatchmakingService(GetOpenLobbiesByTopicUseCase getOpenLobbiesByTopicUseCase,
                              OpenLobbyUseCase openLobbyUseCase,
                              JoinLobbyUseCase joinLobbyUseCase,
                              CancelLobbyUseCase cancelLobbyUseCase,
                              MatchmakingPlayerPort matchmakingPlayerPort,
                              TopicAvailabilityPort topicAvailabilityPort) {
        this.getOpenLobbiesByTopicUseCase = getOpenLobbiesByTopicUseCase;
        this.openLobbyUseCase = openLobbyUseCase;
        this.joinLobbyUseCase = joinLobbyUseCase;
        this.cancelLobbyUseCase = cancelLobbyUseCase;
        this.matchmakingPlayerPort = matchmakingPlayerPort;
        this.topicAvailabilityPort = topicAvailabilityPort;
    }

    @Override
    public CompletableFuture<String> enqueue(MatchmakingCommand.EnqueuePlayerCommand command) {
        PlayerSummary player = matchmakingPlayerPort.getPlayer(command.playerId());

        Set<Language> playerLanguages = languagesOf(player);
        if (!topicAvailabilityPort.coversAllLanguages(command.topicId(), playerLanguages)) {
            throw new LobbyExceptions.TopicNotAvailableInLanguageProblem(command.topicId(), playerLanguages);
        }

        return getOpenLobbiesByTopicUseCase.getOpenByTopicId(command.topicId())
                .thenCompose(lobbies -> joinOrOpen(lobbies, command, player, new HashSet<>()));
    }

    @Override
    public CompletableFuture<String> cancel(MatchmakingCommand.CancelMatchmakingCommand command) {
        return cancelLobbyUseCase.cancel(command.ticketId(), command.playerId());
    }

    private CompletableFuture<String> joinOrOpen(List<Lobby> lobbies,
                                                 MatchmakingCommand.EnqueuePlayerCommand command,
                                                 PlayerSummary player,
                                                 Set<String> excludedLobbies) {
        Optional<Lobby> match = selectBest(lobbies, command.topicId(), command.playerId(), player, excludedLobbies);
        if (match.isEmpty() || excludedLobbies.size() >= MAX_JOIN_ATTEMPTS) {
            return openNewLobby(command);
        }

        Lobby lobby = match.get();
        return joinLobbyUseCase
                .join(lobby.lobbyId(), command.playerId(), LobbyParticipantType.HUMAN)
                .thenApply(_ -> lobby.lobbyId())
                .exceptionallyCompose(error -> {
                    if (isJoinRace(error)) {
                        excludedLobbies.add(lobby.lobbyId());
                        return joinOrOpen(lobbies, command, player, excludedLobbies);
                    }
                    return CompletableFuture.failedFuture(error);
                });
    }

    private CompletableFuture<String> openNewLobby(MatchmakingCommand.EnqueuePlayerCommand command) {
        String lobbyId = UUID.randomUUID().toString();
        return openLobbyUseCase
                .open(new LobbyCommand.OpenLobbyCommand(lobbyId, command.playerId(), command.topicId()))
                .thenApply(_ -> lobbyId);
    }

    private Optional<Lobby> selectBest(List<Lobby> lobbies,
                                       String topicId,
                                       String playerId,
                                       PlayerSummary player,
                                       Set<String> excludedLobbies) {
        List<Lobby> candidates = lobbies.stream()
                .filter(lobby -> !playerId.equals(lobby.initiatorId()))
                .filter(lobby -> !excludedLobbies.contains(lobby.lobbyId()))
                .toList();

        Set<String> onlineInitiators = matchmakingPlayerPort.filterOnline(
                candidates.stream().map(Lobby::initiatorId).distinct().toList());

        return candidates.stream()
                .filter(lobby -> onlineInitiators.contains(lobby.initiatorId()))
                .map(lobby -> Map.entry(lobby, matchmakingPlayerPort.getPlayer(lobby.initiatorId())))
                .filter(entry -> Math.abs(entry.getValue().level() - player.level()) <= MatchmakingRules.LEVEL_WINDOW)
                .filter(entry -> topicAvailabilityPort.coversAllLanguages(
                        topicId, languagesOf(entry.getValue(), player)))
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

    /** Langues non nulles des joueurs fournis (union). */
    private static Set<Language> languagesOf(PlayerSummary... players) {
        Set<Language> languages = new HashSet<>();
        for (PlayerSummary player : players) {
            if (player.language() != null) {
                languages.add(player.language());
            }
        }
        return languages;
    }

    /** Course de join : le lobby a changé d'état entre la sélection et la commande. */
    private static boolean isJoinRace(Throwable error) {
        Throwable current = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        while (current != null) {
            if (current instanceof LobbyExceptions.LobbyNotAvailableProblem
                    || current instanceof LobbyExceptions.LobbyAlreadyFullProblem
                    || current instanceof LobbyExceptions.PlayerAlreadyInLobbyProblem) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
