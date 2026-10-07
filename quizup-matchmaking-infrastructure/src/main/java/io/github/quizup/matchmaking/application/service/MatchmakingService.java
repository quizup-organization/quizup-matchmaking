package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.model.MatchmakingRules;
import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingStorePort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de l'appariement public (« Défier le monde ») sur le store chaud Redis.
 *
 * <p>Le dernier arrivé initie le pairage : il cherche un candidat plus ancien, compatible
 * (même sujet, niveau ±{@code LEVEL_WINDOW}, langues couvertes par le sujet), le réclame
 * atomiquement (Lua), crée la partie puis finalise les deux tickets. À l'échéance (5 s) sans
 * adversaire, le timer bascule sur une partie bot. Plus de saga ni de read model asynchrone
 * sur le chemin de décision.</p>
 */
@Service
public class MatchmakingService {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingService.class);
    private static final int CANDIDATE_LIMIT = 10;

    private final MatchmakingStorePort store;
    private final CommandGateway commandGateway;
    private final MatchmakingPlayerPort playerPort;
    private final TopicAvailabilityPort topicAvailabilityPort;

    public MatchmakingService(MatchmakingStorePort store,
                              CommandGateway commandGateway,
                              MatchmakingPlayerPort playerPort,
                              TopicAvailabilityPort topicAvailabilityPort) {
        this.store = store;
        this.commandGateway = commandGateway;
        this.playerPort = playerPort;
        this.topicAvailabilityPort = topicAvailabilityPort;
    }

    /** Enfile un ticket et tente immédiatement un pairage avec un candidat plus ancien. */
    public void create(MatchmakingCommand.CreateMatchmakingCommand command) {
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.matchmakingId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new LobbyExceptions.MissingTopicIdentifierProblem(command.matchmakingId());
        }
        Instant now = Instant.now();
        Matchmaking ticket = Matchmaking.builder()
                .matchmakingId(command.matchmakingId())
                .playerId(command.playerId())
                .topicId(command.topicId())
                .level(command.level())
                .languages(command.languages() == null ? Set.of() : Set.copyOf(command.languages()))
                .vsBot(false)
                .status(MatchmakingStatus.SEARCHING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        logger.info("Enqueue matchmaking: matchmakingId={}, playerId={}, topicId={}, level={}",
                ticket.matchmakingId(), ticket.playerId(), ticket.topicId(), ticket.level());
        store.enqueue(ticket, new MatchmakingEvent.MatchmakingStartedEvent(
                ticket.matchmakingId(), ticket.playerId(), ticket.topicId(),
                ticket.level(), ticket.languages(), now));
        tryMatch(ticket);
    }

    /** Annule une recherche encore active (idempotent, propriétaire uniquement). */
    public void cancel(MatchmakingCommand.CancelMatchmakingCommand command) {
        Matchmaking ticket = store.findById(command.matchmakingId())
                .orElseThrow(() -> new LobbyExceptions.MatchmakingNotFoundProblem(command.matchmakingId()));
        if (ticket.status() != MatchmakingStatus.SEARCHING) {
            return;
        }
        if (!command.playerId().equals(ticket.playerId())) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(ticket.matchmakingId(), command.playerId());
        }
        store.cancel(ticket.matchmakingId(), new MatchmakingEvent.MatchmakingCancelledEvent(
                ticket.matchmakingId(), "PLAYER_CANCELLED", Instant.now()));
    }

    /** Échéance d'appariement : bascule sur une partie bot si le ticket cherche encore. */
    public void onDeadline(String ticketId) {
        Optional<Matchmaking> maybeTicket = store.findById(ticketId);
        if (maybeTicket.isEmpty() || maybeTicket.get().status() != MatchmakingStatus.SEARCHING) {
            return;
        }
        Matchmaking ticket = maybeTicket.get();
        if (!store.claimForBot(ticketId)) {
            return;
        }
        logger.info("Timeout matchmaking — partie bot: matchmakingId={}", ticketId);
        createBotGame(ticket);
    }

    private void tryMatch(Matchmaking mine) {
        for (Matchmaking candidate : store.candidates(mine.topicId(), mine.createdAt(), CANDIDATE_LIMIT)) {
            if (Math.abs(candidate.level() - mine.level()) > MatchmakingRules.LEVEL_WINDOW) {
                continue;
            }
            Set<Language> union = union(mine.languages(), candidate.languages());
            if (!topicAvailabilityPort.coversAllLanguages(mine.topicId(), union)) {
                continue;
            }
            if (!store.claimPair(mine.matchmakingId(), candidate.matchmakingId())) {
                continue; // course : candidat déjà apparié ou annulé
            }
            createHumanGame(mine, candidate, union);
            return;
        }
    }

    private void createHumanGame(Matchmaking mine, Matchmaking candidate, Set<Language> union) {
        String gameId = UUID.randomUUID().toString();
        try {
            PlayerSummary me = playerPort.getPlayer(mine.playerId());
            PlayerSummary opponent = playerPort.getPlayer(candidate.playerId());
            commandGateway.sendAndWait(new GameCommand.CreateGameCommand(
                    gameId,
                    mine.topicId(),
                    mine.playerId(),
                    nameOf(me),
                    candidate.playerId(),
                    nameOf(opponent),
                    union,
                    GamePlayerType.HUMAN,
                    null));
            Instant now = Instant.now();
            store.completePair(mine.matchmakingId(), candidate.matchmakingId(),
                    new MatchmakingEvent.MatchmakingMatchedEvent(
                            mine.matchmakingId(), candidate.playerId(), gameId, false, now),
                    new MatchmakingEvent.MatchmakingMatchedEvent(
                            candidate.matchmakingId(), mine.playerId(), gameId, false, now));
        } catch (Exception exception) {
            logger.error("Échec de création de partie pour l'appariement {} / {}",
                    mine.matchmakingId(), candidate.matchmakingId(), exception);
            Instant now = Instant.now();
            store.fail(mine.matchmakingId(), new MatchmakingEvent.MatchmakingFailedEvent(
                    mine.matchmakingId(), "GAME_CREATION_FAILED", now));
            store.fail(candidate.matchmakingId(), new MatchmakingEvent.MatchmakingFailedEvent(
                    candidate.matchmakingId(), "GAME_CREATION_FAILED", now));
        }
    }

    private void createBotGame(Matchmaking ticket) {
        String gameId = UUID.randomUUID().toString();
        try {
            PlayerSummary me = playerPort.getPlayer(ticket.playerId());
            commandGateway.sendAndWait(new GameCommand.CreateGameCommand(
                    gameId,
                    ticket.topicId(),
                    ticket.playerId(),
                    nameOf(me),
                    QuizUpConstants.SYSTEM_USER_ID,
                    QuizUpConstants.SYSTEM_USER_NAME,
                    ticket.languages() == null ? Set.of() : Set.copyOf(ticket.languages()),
                    GamePlayerType.BOT,
                    null));
            store.completeBot(ticket.matchmakingId(), new MatchmakingEvent.MatchmakingMatchedEvent(
                    ticket.matchmakingId(), null, gameId, true, Instant.now()));
        } catch (Exception exception) {
            logger.error("Échec de création de partie bot: matchmakingId={}", ticket.matchmakingId(), exception);
            store.fail(ticket.matchmakingId(), new MatchmakingEvent.MatchmakingFailedEvent(
                    ticket.matchmakingId(), "GAME_CREATION_FAILED", Instant.now()));
        }
    }

    private static Set<Language> union(Set<Language> left, Set<Language> right) {
        Set<Language> union = new HashSet<>();
        if (left != null) {
            union.addAll(left);
        }
        if (right != null) {
            union.addAll(right);
        }
        return union;
    }

    private static String nameOf(PlayerSummary player) {
        return player == null || player.pseudonym() == null ? "" : player.pseudonym();
    }
}
