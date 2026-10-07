package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.model.ChallengeDeadline;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.port.out.ChallengeStorePort;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration du défi nominatif (intention asynchrone, TTL 1 h) sur le store chaud Redis.
 *
 * <p>L'acceptation crée la salle temps réel (identifiant déterministe) et la relie au défi :
 * la salle porte ensuite présence, compte à rebours et partie. Plus de saga ni de projection ;
 * la rétention est native (TTL Redis).</p>
 */
@Service
public class ChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeService.class);

    private final ChallengeStorePort store;
    private final LobbyService lobbyService;
    private final ProfileRepositoryPort profileRepositoryPort;
    private final TopicAvailabilityPort topicAvailabilityPort;

    public ChallengeService(ChallengeStorePort store,
                            LobbyService lobbyService,
                            ProfileRepositoryPort profileRepositoryPort,
                            TopicAvailabilityPort topicAvailabilityPort) {
        this.store = store;
        this.lobbyService = lobbyService;
        this.profileRepositoryPort = profileRepositoryPort;
        this.topicAvailabilityPort = topicAvailabilityPort;
    }

    /** Ouvre un défi nominatif ; le thème doit couvrir les langues des deux joueurs. */
    public void create(ChallengeCommand.CreateChallengeCommand command) {
        if (StringUtils.isBlank(command.challengerId())) {
            throw new ChallengeExceptions.MissingChallengerIdentifierProblem(command.challengeId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new ChallengeExceptions.MissingTopicIdentifierProblem(command.challengeId());
        }
        if (StringUtils.isBlank(command.opponentId())) {
            throw new ChallengeExceptions.MissingOpponentIdentifierProblem(command.challengeId());
        }
        if (command.opponentId().equals(command.challengerId())) {
            throw new ChallengeExceptions.CannotChallengeSelfProblem(command.challengeId(), command.challengerId());
        }
        requireTopicCoversLanguages(command.challengerId(), command.opponentId(), command.topicId(), command.challengeId());

        Instant now = Instant.now();
        Challenge challenge = Challenge.builder()
                .challengeId(command.challengeId())
                .topicId(command.topicId())
                .challengerId(command.challengerId())
                .opponentId(command.opponentId())
                .status(ChallengeStatus.PENDING)
                .createdAt(now)
                .expiresAt(now.plus(ChallengeDeadline.CHALLENGE_EXPIRY_DURATION))
                .build();
        logger.info("Creating challenge: challengeId={}, topicId={}, challengerId={}, opponentId={}",
                challenge.challengeId(), challenge.topicId(), challenge.challengerId(), challenge.opponentId());
        store.create(challenge, new ChallengeEvent.ChallengeCreatedEvent(
                challenge.challengeId(), challenge.topicId(), challenge.challengerId(),
                challenge.opponentId(), challenge.expiresAt(), now));
    }

    /** L'invité accepte : la salle temps réel est créée (idempotent). */
    public void accept(ChallengeCommand.AcceptChallengeCommand command) {
        Challenge challenge = requireChallenge(command.challengeId());
        ChallengeStorePort.AcceptOutcome outcome = store.accept(command.challengeId(), command.playerId(),
                new ChallengeEvent.ChallengeAcceptedEvent(
                        command.challengeId(), challenge.topicId(), challenge.challengerId(),
                        challenge.opponentId(), Instant.now()));
        switch (outcome) {
            case NOT_PENDING -> throw new ChallengeExceptions.ChallengeNotPendingProblem(
                    command.challengeId(), challenge.status().name());
            case NOT_OPPONENT -> throw new ChallengeExceptions.ChallengeNotInvitedProblem(
                    command.challengeId(), command.playerId());
            case ACCEPTED, IDEMPOTENT -> createRoom(command.challengeId());
        }
    }

    public void decline(ChallengeCommand.DeclineChallengeCommand command) {
        Challenge challenge = requireChallenge(command.challengeId());
        if (!command.playerId().equals(challenge.opponentId())) {
            throw new ChallengeExceptions.ChallengeNotInvitedProblem(command.challengeId(), command.playerId());
        }
        logger.info("Declining challenge: challengeId={}, opponentId={}", command.challengeId(), command.playerId());
        store.decline(command.challengeId(), new ChallengeEvent.ChallengeDeclinedEvent(
                command.challengeId(), challenge.challengerId(), challenge.opponentId(), Instant.now()));
    }

    public void cancel(ChallengeCommand.CancelChallengeCommand command) {
        Challenge challenge = requireChallenge(command.challengeId());
        if (challenge.status() == ChallengeStatus.PENDING
                && !command.playerId().equals(challenge.challengerId())) {
            throw new ChallengeExceptions.PlayerNotChallengerProblem(command.challengeId(), command.playerId());
        }
        store.cancel(command.challengeId(), new ChallengeEvent.ChallengeCancelledEvent(
                command.challengeId(), challenge.challengerId(), Instant.now()));
    }

    /** Expiration (timer) : no-op si le défi a déjà été résolu. */
    public void expire(String challengeId) {
        store.expire(challengeId, new ChallengeEvent.ChallengeExpiredEvent(challengeId, Instant.now()));
    }

    public Challenge get(String challengeId) {
        return requireChallenge(challengeId);
    }

    public List<Challenge> findMine(String playerId) {
        return store.findPendingByPlayerId(playerId);
    }

    private void createRoom(String challengeId) {
        Challenge challenge = requireChallenge(challengeId);
        if (StringUtils.isNotBlank(challenge.roomId())) {
            return; // acceptation déjà traitée
        }
        String roomId = deterministicRoomId(challengeId);
        logger.info("Défi accepté → création de la salle: challengeId={}, roomId={}", challengeId, roomId);
        lobbyService.create(new LobbyCommand.CreateLobbyCommand(
                roomId, challenge.topicId(), challenge.challengerId(), challenge.opponentId()));
        // L'invité est connu : on le marque comme ayant rejoint la salle (le lanceur entrera
        // via EnterLobbyRoom quand son client ouvrira la salle).
        lobbyService.joinInvitee(roomId, challenge.opponentId());
        store.linkRoom(challengeId, roomId, new ChallengeEvent.ChallengeRoomCreatedEvent(
                challengeId, roomId, Instant.now()));
    }

    private static String deterministicRoomId(String challengeId) {
        return UUID.nameUUIDFromBytes(("room:" + challengeId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Challenge requireChallenge(String challengeId) {
        return store.findChallengeById(challengeId)
                .orElseThrow(() -> new ChallengeExceptions.ChallengeNotFoundProblem(challengeId));
    }

    private void requireTopicCoversLanguages(String challengerId,
                                             String opponentId,
                                             String topicId,
                                             String challengeId) {
        Set<Language> languages = new HashSet<>();
        LobbyPlayer challenger = profileRepositoryPort.getById(challengerId);
        LobbyPlayer opponent = profileRepositoryPort.getById(opponentId);
        if (challenger != null && challenger.language() != null) {
            languages.add(challenger.language());
        }
        if (opponent != null && opponent.language() != null) {
            languages.add(opponent.language());
        }
        if (!languages.isEmpty() && !topicAvailabilityPort.coversAllLanguages(topicId, languages)) {
            throw new ChallengeExceptions.TopicNotAvailableInLanguageProblem(challengeId, topicId, languages);
        }
    }
}
