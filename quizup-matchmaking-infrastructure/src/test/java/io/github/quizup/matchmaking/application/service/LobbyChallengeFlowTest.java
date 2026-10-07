package io.github.quizup.matchmaking.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.model.SessionTimer;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.matchmaking.infrastructure.out.redis.RedisLobbyChallengeStore;
import io.github.quizup.matchmaking.infrastructure.out.redis.RedisSessionStore;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test d'intégration (Testcontainers Redis) du store chaud lobby/challenge et des services
 * associés : cycle de vie complet, ready-check → partie, garde nominative, sortie non
 * destructive, acceptation de défi → salle, timers.
 */
class LobbyChallengeFlowTest {

    private static final String TOPIC = "topic-1";

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;

    private RedisLobbyChallengeStore store;
    private RedisSessionStore sessionStore;
    private CommandGateway commandGateway;
    private LobbyService lobbyService;
    private ChallengeService challengeService;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = new LettuceConnectionFactory(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        store = new RedisLobbyChallengeStore(redis, mapper);
        sessionStore = new RedisSessionStore(redis, mapper);

        commandGateway = mock(CommandGateway.class);
        when(commandGateway.sendAndWait(any())).thenReturn(null);
        ProfileRepositoryPort profilePort = mock(ProfileRepositoryPort.class);
        when(profilePort.getById(any())).thenAnswer(invocation -> new LobbyPlayer(
                invocation.getArgument(0), invocation.getArgument(0) + "@quizup.local",
                "name-" + invocation.getArgument(0), Language.FR));
        TopicAvailabilityPort topicPort = mock(TopicAvailabilityPort.class);
        when(topicPort.coversAllLanguages(any(), any())).thenReturn(true);

        lobbyService = new LobbyService(store, commandGateway, profilePort, topicPort);
        challengeService = new ChallengeService(store, lobbyService, profilePort, topicPort);
    }

    @Test
    void sharedLobby_lifecycle_readyCheckCreatesGame() {
        lobbyService.create(new LobbyCommand.CreateLobbyCommand("l1", TOPIC, "u1", null));
        lobbyService.join(new LobbyCommand.JoinLobbyCommand("l1", "u2"));
        lobbyService.enter(new LobbyCommand.EnterLobbyRoomCommand("l1", "u1"));
        lobbyService.enter(new LobbyCommand.EnterLobbyRoomCommand("l1", "u2"));

        Lobby ready = lobbyService.get("l1");
        assertThat(ready.status()).isEqualTo(LobbyStatus.CREATED);
        assertThat(ready.initiatorPresent()).isTrue();
        assertThat(ready.participantPresent()).isTrue();
        assertThat(ready.readyDeadlineAt()).isNotNull();

        lobbyService.onReadyCheck("l1");

        ArgumentCaptor<GameCommand.CreateGameCommand> game =
                ArgumentCaptor.forClass(GameCommand.CreateGameCommand.class);
        verify(commandGateway).sendAndWait(game.capture());
        assertThat(game.getValue().player1Id()).isEqualTo("u1");
        assertThat(game.getValue().player2Id()).isEqualTo("u2");
        assertThat(game.getValue().player2Type()).isEqualTo(GamePlayerType.HUMAN);

        Lobby completed = lobbyService.get("l1");
        assertThat(completed.status()).isEqualTo(LobbyStatus.CLOSED);
        assertThat(completed.gameId()).isNotBlank();
    }

    @Test
    void nominativeLobby_refusesOtherPlayers_andInviteeJoins() {
        lobbyService.create(new LobbyCommand.CreateLobbyCommand("l1", TOPIC, "u1", "u2"));

        assertThatThrownBy(() -> lobbyService.join(new LobbyCommand.JoinLobbyCommand("l1", "u3")))
                .isInstanceOf(LobbyExceptions.LobbyNotInvitedProblem.class);

        lobbyService.join(new LobbyCommand.JoinLobbyCommand("l1", "u2"));
        assertThat(lobbyService.get("l1").participantId()).isEqualTo("u2");
    }

    @Test
    void leave_isNonDestructive_andReenterRelaunchesReadyCheck() {
        lobbyService.create(new LobbyCommand.CreateLobbyCommand("l1", TOPIC, "u1", null));
        lobbyService.join(new LobbyCommand.JoinLobbyCommand("l1", "u2"));
        lobbyService.enter(new LobbyCommand.EnterLobbyRoomCommand("l1", "u1"));
        lobbyService.enter(new LobbyCommand.EnterLobbyRoomCommand("l1", "u2"));

        lobbyService.leave(new LobbyCommand.LeaveLobbyCommand("l1", "u2"));

        Lobby left = lobbyService.get("l1");
        assertThat(left.status()).isEqualTo(LobbyStatus.CREATED);
        assertThat(left.participantPresent()).isFalse();
        assertThat(left.participantId()).isEqualTo("u2");

        lobbyService.enter(new LobbyCommand.EnterLobbyRoomCommand("l1", "u2"));
        assertThat(lobbyService.get("l1").participantPresent()).isTrue();
    }

    @Test
    void lobbyExpiryTimer_isScheduled_andExpireClosesLobby() {
        lobbyService.create(new LobbyCommand.CreateLobbyCommand("l1", TOPIC, "u1", null));

        List<SessionTimer.Due> due = sessionStore.claimDue(Instant.now().plusSeconds(2 * 24 * 3600), 10);
        assertThat(due).contains(new SessionTimer.Due(SessionTimer.LOBBY_EXPIRY, "l1"));

        lobbyService.expire("l1");

        assertThat(lobbyService.get("l1").status()).isEqualTo(LobbyStatus.CLOSED);
    }

    @Test
    void challenge_accept_createsLinkedRoom() {
        challengeService.create(new ChallengeCommand.CreateChallengeCommand("c1", TOPIC, "u1", "u2"));

        assertThatThrownBy(() -> challengeService.accept(
                new ChallengeCommand.AcceptChallengeCommand("c1", "u1")))
                .isInstanceOf(ChallengeExceptions.ChallengeNotInvitedProblem.class);

        challengeService.accept(new ChallengeCommand.AcceptChallengeCommand("c1", "u2"));

        Challenge challenge = challengeService.get("c1");
        assertThat(challenge.status()).isEqualTo(ChallengeStatus.ACCEPTED);
        assertThat(challenge.roomId()).isNotBlank();

        Lobby room = lobbyService.get(challenge.roomId());
        assertThat(room.initiatorId()).isEqualTo("u1");
        assertThat(room.participantId()).isEqualTo("u2");
        assertThat(room.status()).isEqualTo(LobbyStatus.CREATED);
    }

    @Test
    void challengeExpiryTimer_isScheduled_andExpireResolvesChallenge() {
        challengeService.create(new ChallengeCommand.CreateChallengeCommand("c1", TOPIC, "u1", "u2"));

        List<SessionTimer.Due> due = sessionStore.claimDue(Instant.now().plusSeconds(2 * 3600), 10);
        assertThat(due).contains(new SessionTimer.Due(SessionTimer.CHALLENGE_EXPIRY, "c1"));

        challengeService.expire("c1");

        assertThat(challengeService.get("c1").status()).isEqualTo(ChallengeStatus.EXPIRED);
    }
}
