package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyDeadline;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.port.out.LobbyStorePort;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de la salle temps réel (deux humains) sur le store chaud Redis.
 *
 * <p>Présence et ready-check vivent dans le hash du salon (TTL 1 jour) ; quand les deux joueurs
 * sont entrés, un timer {@code LOBBY_READY_CHECK} (3 s) déclenche la création de la partie et la
 * clôture du salon. Une sortie est non destructive (le joueur peut revenir), un timer obsolète est
 * un no-op. Plus de saga, de projection ni de purge.</p>
 */
@Service
public class LobbyService {

    private static final Logger logger = LoggerFactory.getLogger(LobbyService.class);

    private final LobbyStorePort store;
    private final CommandGateway commandGateway;
    private final ProfileRepositoryPort profileRepositoryPort;
    private final TopicAvailabilityPort topicAvailabilityPort;

    public LobbyService(LobbyStorePort store,
                        CommandGateway commandGateway,
                        ProfileRepositoryPort profileRepositoryPort,
                        TopicAvailabilityPort topicAvailabilityPort) {
        this.store = store;
        this.commandGateway = commandGateway;
        this.profileRepositoryPort = profileRepositoryPort;
        this.topicAvailabilityPort = topicAvailabilityPort;
    }

    /** Ouvre un salon privé ; si {@code opponentId} est renseigné, le thème doit couvrir les deux langues. */
    public void create(LobbyCommand.CreateLobbyCommand command) {
        if (StringUtils.isBlank(command.initiatorId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new LobbyExceptions.MissingTopicIdentifierProblem(command.lobbyId());
        }
        if (StringUtils.isNotBlank(command.opponentId())) {
            if (command.opponentId().equals(command.initiatorId())) {
                throw new LobbyExceptions.CannotChallengeSelfProblem(command.lobbyId(), command.initiatorId());
            }
            requireTopicCoversLanguages(command.initiatorId(), command.opponentId(), command.topicId());
        }
        Instant now = Instant.now();
        Lobby lobby = Lobby.builder()
                .lobbyId(command.lobbyId())
                .topicId(command.topicId())
                .initiatorId(command.initiatorId())
                .opponentId(command.opponentId())
                .status(LobbyStatus.CREATED)
                .createdAt(now)
                .expiresAt(now.plus(LobbyDeadline.LOBBY_EXPIRY_DURATION))
                .updatedAt(now)
                .build();
        logger.info("Creating lobby: lobbyId={}, topicId={}, initiatorId={}, opponentId={}",
                lobby.lobbyId(), lobby.topicId(), lobby.initiatorId(), lobby.opponentId());
        store.create(lobby, new LobbyEvent.LobbyCreatedEvent(
                lobby.lobbyId(), lobby.topicId(), lobby.initiatorId(), lobby.opponentId(),
                lobby.expiresAt(), now));
    }

    public void join(LobbyCommand.JoinLobbyCommand command) {
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        LobbyEvent.LobbyJoinedEvent event = new LobbyEvent.LobbyJoinedEvent(
                command.lobbyId(), command.playerId(), Instant.now());
        switch (store.join(command.lobbyId(), command.playerId(), event)) {
            case JOINED, IDEMPOTENT -> logger.info("Salon rejoint: lobbyId={}, participantId={}",
                    command.lobbyId(), command.playerId());
            case NOT_INVITED -> throw new LobbyExceptions.LobbyNotInvitedProblem(command.lobbyId(), command.playerId());
            case FULL -> throw new LobbyExceptions.LobbyAlreadyFullProblem(command.lobbyId());
            case CLOSED -> throw new LobbyExceptions.LobbyNotAvailableProblem(command.lobbyId());
        }
    }

    /** Entrée effective dans la salle (présence temps réel, idempotent). */
    public void enter(LobbyCommand.EnterLobbyRoomCommand command) {
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        Instant now = Instant.now();
        LobbyEvent.LobbyRoomEnteredEvent enteredEvent = new LobbyEvent.LobbyRoomEnteredEvent(
                command.lobbyId(), command.playerId(), now);
        LobbyEvent.LobbyAllPlayersPresentEvent allPresentEvent = new LobbyEvent.LobbyAllPlayersPresentEvent(
                command.lobbyId(), now.plus(LobbyDeadline.LOBBY_READY_CHECK_DURATION), now);
        switch (store.enter(command.lobbyId(), command.playerId(), enteredEvent, allPresentEvent)) {
            case ENTERED -> logger.debug("Entrée en salle: lobbyId={}, playerId={}",
                    command.lobbyId(), command.playerId());
            case BOTH_PRESENT -> logger.info("Les deux joueurs sont présents, lancement dans {}s: lobbyId={}",
                    LobbyDeadline.LOBBY_READY_CHECK_DURATION.toSeconds(), command.lobbyId());
            case IDEMPOTENT -> {
                // déjà présent
            }
            case NOT_PARTICIPANT -> throw new LobbyExceptions.PlayerNotInLobbyProblem(
                    command.lobbyId(), command.playerId());
            case CLOSED -> {
                // salon terminé : no-op
            }
        }
    }

    /** Sortie non destructive : le joueur peut revenir jusqu'à l'expiration du salon. */
    public void leave(LobbyCommand.LeaveLobbyCommand command) {
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        LobbyEvent.LobbyLeftEvent event = new LobbyEvent.LobbyLeftEvent(
                command.lobbyId(), command.playerId(), Instant.now());
        switch (store.leave(command.lobbyId(), command.playerId(), event)) {
            case LEFT -> logger.info("Joueur sorti de la salle (retour possible): lobbyId={}, playerId={}",
                    command.lobbyId(), command.playerId());
            case IDEMPOTENT, CLOSED -> {
                // déjà sorti ou salon terminé
            }
            case NOT_PARTICIPANT -> throw new LobbyExceptions.PlayerNotInLobbyProblem(
                    command.lobbyId(), command.playerId());
        }
    }

    /** Annulation explicite par l'initiateur. */
    public void cancel(LobbyCommand.CancelLobbyCommand command) {
        Lobby lobby = requireLobby(command.lobbyId());
        if (isClosed(lobby)) {
            return;
        }
        if (!command.playerId().equals(lobby.initiatorId())) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(command.lobbyId(), command.playerId());
        }
        store.cancel(command.lobbyId(), new LobbyEvent.LobbyCancelledEvent(
                command.lobbyId(), lobby.initiatorId(), "PLAYER_CANCELLED", Instant.now()));
    }

    /** Refus d'un défi nominatif par l'invité. */
    public void decline(LobbyCommand.DeclineLobbyCommand command) {
        Lobby lobby = requireLobby(command.lobbyId());
        if (isClosed(lobby)) {
            return;
        }
        if (StringUtils.isBlank(lobby.opponentId()) || !command.playerId().equals(lobby.opponentId())) {
            throw new LobbyExceptions.LobbyNotInvitedProblem(command.lobbyId(), command.playerId());
        }
        logger.info("Declining lobby: lobbyId={}, opponentId={}", command.lobbyId(), command.playerId());
        store.decline(command.lobbyId(), new LobbyEvent.LobbyDeclinedEvent(
                command.lobbyId(), lobby.initiatorId(), lobby.opponentId(), Instant.now()));
    }

    /** Un joueur a disparu (présence) : la salle est close. */
    public void miss(String lobbyId, String absentPlayerId, String reason) {
        store.miss(lobbyId, absentPlayerId, reason, new LobbyEvent.LobbyMissedEvent(
                lobbyId, absentPlayerId, reason, Instant.now()));
    }

    /** Expiration de la salle (timer). */
    public void expire(String lobbyId) {
        logger.info("Expiration du salon: lobbyId={}", lobbyId);
        store.expire(lobbyId, new LobbyEvent.LobbyExpiredEvent(lobbyId, Instant.now()));
    }

    /** Fin du compte à rebours : crée la partie si les deux joueurs sont toujours présents. */
    public void onReadyCheck(String lobbyId) {
        lobby(lobbyId).ifPresent(lobby -> {
            if (lobby.status() != LobbyStatus.CREATED
                    || !lobby.initiatorPresent()
                    || !lobby.participantPresent()
                    || StringUtils.isBlank(lobby.participantId())) {
                return;
            }
            createGameAndComplete(lobby);
        });
    }

    public Lobby get(String lobbyId) {
        return requireLobby(lobbyId);
    }

    public List<Lobby> findMine(String playerId) {
        return store.findOpenByPlayerId(playerId);
    }

    /** Jointure interne (invité d'un défi accepté). */
    public void joinInvitee(String lobbyId, String playerId) {
        join(new LobbyCommand.JoinLobbyCommand(lobbyId, playerId));
    }

    private void createGameAndComplete(Lobby lobby) {
        LobbyPlayer initiator = profileRepositoryPort.getById(lobby.initiatorId());
        LobbyPlayer participant = profileRepositoryPort.getById(lobby.participantId());
        if (initiator == null || participant == null) {
            logger.error("Profils introuvables pour la salle: lobbyId={}", lobby.lobbyId());
            store.fail(lobby.lobbyId(), "PROFILE_NOT_FOUND", new LobbyEvent.LobbyFailedEvent(
                    lobby.lobbyId(), "PROFILE_NOT_FOUND", Instant.now()));
            return;
        }

        String gameId = UUID.randomUUID().toString();
        try {
            commandGateway.sendAndWait(new GameCommand.CreateGameCommand(
                    gameId,
                    lobby.topicId(),
                    initiator.playerId(),
                    initiator.playerName(),
                    participant.playerId(),
                    participant.playerName(),
                    languagesOf(initiator, participant),
                    GamePlayerType.HUMAN,
                    null));
            store.complete(lobby.lobbyId(), gameId, new LobbyEvent.LobbyCompletedEvent(
                    lobby.lobbyId(), gameId, Instant.now()));
            logger.info("Partie créée depuis la salle: lobbyId={}, gameId={}", lobby.lobbyId(), gameId);
        } catch (Exception exception) {
            logger.error("Échec de création de partie depuis la salle: lobbyId={}", lobby.lobbyId(), exception);
            store.fail(lobby.lobbyId(), "GAME_CREATION_FAILED", new LobbyEvent.LobbyFailedEvent(
                    lobby.lobbyId(), "GAME_CREATION_FAILED", Instant.now()));
        }
    }

    private java.util.Optional<Lobby> lobby(String lobbyId) {
        return store.findById(lobbyId);
    }

    private Lobby requireLobby(String lobbyId) {
        return store.findById(lobbyId)
                .orElseThrow(() -> new LobbyExceptions.LobbyNotFoundProblem(lobbyId));
    }

    private static boolean isClosed(Lobby lobby) {
        return lobby.status() == LobbyStatus.CLOSED || lobby.status() == LobbyStatus.FAILED;
    }

    private void requireTopicCoversLanguages(String initiatorId, String opponentId, String topicId) {
        Set<Language> languages = new HashSet<>();
        LobbyPlayer initiator = profileRepositoryPort.getById(initiatorId);
        LobbyPlayer opponent = profileRepositoryPort.getById(opponentId);
        if (initiator != null && initiator.language() != null) {
            languages.add(initiator.language());
        }
        if (opponent != null && opponent.language() != null) {
            languages.add(opponent.language());
        }
        if (!languages.isEmpty() && !topicAvailabilityPort.coversAllLanguages(topicId, languages)) {
            throw new LobbyExceptions.TopicNotAvailableInLanguageProblem(topicId, languages);
        }
    }

    private static Set<Language> languagesOf(LobbyPlayer... players) {
        Set<Language> languages = new HashSet<>();
        for (LobbyPlayer player : players) {
            if (player != null && player.language() != null) {
                languages.add(player.language());
            }
        }
        return languages;
    }
}
