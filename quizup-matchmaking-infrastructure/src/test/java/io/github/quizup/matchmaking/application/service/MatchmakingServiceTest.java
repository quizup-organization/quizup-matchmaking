package io.github.quizup.matchmaking.application.service;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.in.CancelLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetOpenLobbiesByTopicUseCase;
import io.github.quizup.matchmaking.domain.port.in.JoinLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.OpenLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchmakingServiceTest {

    private static final String PLAYER = "player-1";
    private static final String TOPIC = "topic-1";

    private final GetOpenLobbiesByTopicUseCase getOpenLobbies = mock(GetOpenLobbiesByTopicUseCase.class);
    private final OpenLobbyUseCase openLobby = mock(OpenLobbyUseCase.class);
    private final JoinLobbyUseCase joinLobby = mock(JoinLobbyUseCase.class);
    private final CancelLobbyUseCase cancelLobby = mock(CancelLobbyUseCase.class);
    private final MatchmakingPlayerPort playerPort = mock(MatchmakingPlayerPort.class);

    private final MatchmakingService service = new MatchmakingService(
            getOpenLobbies, openLobby, joinLobby, cancelLobby, playerPort);

    @BeforeEach
    void setUp() {
        when(playerPort.getPlayer(PLAYER)).thenReturn(new PlayerSummary(PLAYER, "Moi", 10, "FR"));
        when(openLobby.open(any(LobbyCommand.OpenLobbyCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("opened"));
    }

    @Test
    void enqueue_opensLobby_whenNoCandidate() {
        when(getOpenLobbies.getOpenByTopicId(TOPIC)).thenReturn(CompletableFuture.completedFuture(List.of()));

        String ticketId = service.enqueue(PLAYER, TOPIC).join();

        assertThat(ticketId).isNotBlank();
        verify(openLobby).open(any(LobbyCommand.OpenLobbyCommand.class));
        verify(joinLobby, never()).join(anyString(), anyString(), any(LobbyParticipantType.class));
    }

    @Test
    void enqueue_skipsOfflineInitiators_andOpensLobby() {
        Lobby lobby = lobby("lobby-1", "initiator-1");
        when(getOpenLobbies.getOpenByTopicId(TOPIC)).thenReturn(CompletableFuture.completedFuture(List.of(lobby)));
        when(playerPort.filterOnline(any())).thenReturn(Set.of());

        service.enqueue(PLAYER, TOPIC).join();

        verify(joinLobby, never()).join(anyString(), anyString(), any(LobbyParticipantType.class));
        verify(openLobby).open(any(LobbyCommand.OpenLobbyCommand.class));
    }

    @Test
    void enqueue_joinsOnlineCandidate() {
        Lobby lobby = lobby("lobby-1", "initiator-1");
        when(getOpenLobbies.getOpenByTopicId(TOPIC)).thenReturn(CompletableFuture.completedFuture(List.of(lobby)));
        when(playerPort.filterOnline(any())).thenReturn(Set.of("initiator-1"));
        when(playerPort.getPlayer("initiator-1")).thenReturn(new PlayerSummary("initiator-1", "Adv", 12, "FR"));
        when(joinLobby.join("lobby-1", PLAYER, LobbyParticipantType.HUMAN))
                .thenReturn(CompletableFuture.completedFuture(null));

        String ticketId = service.enqueue(PLAYER, TOPIC).join();

        assertThat(ticketId).isEqualTo("lobby-1");
        verify(joinLobby).join("lobby-1", PLAYER, LobbyParticipantType.HUMAN);
    }

    @Test
    void enqueue_retriesNextCandidate_whenJoinRace() {
        Lobby first = lobby("lobby-1", "initiator-1");
        Lobby second = lobby("lobby-2", "initiator-2");
        when(getOpenLobbies.getOpenByTopicId(TOPIC)).thenReturn(CompletableFuture.completedFuture(List.of(first, second)));
        when(playerPort.filterOnline(any())).thenReturn(Set.of("initiator-1", "initiator-2"));
        when(playerPort.getPlayer("initiator-1")).thenReturn(new PlayerSummary("initiator-1", "A", 10, "FR"));
        when(playerPort.getPlayer("initiator-2")).thenReturn(new PlayerSummary("initiator-2", "B", 14, "FR"));
        when(joinLobby.join("lobby-1", PLAYER, LobbyParticipantType.HUMAN))
                .thenReturn(CompletableFuture.failedFuture(new LobbyExceptions.LobbyNotAvailableProblem("lobby-1")));
        when(joinLobby.join("lobby-2", PLAYER, LobbyParticipantType.HUMAN))
                .thenReturn(CompletableFuture.completedFuture(null));

        String ticketId = service.enqueue(PLAYER, TOPIC).join();

        assertThat(ticketId).isEqualTo("lobby-2");
        verify(joinLobby).join("lobby-1", PLAYER, LobbyParticipantType.HUMAN);
        verify(joinLobby).join("lobby-2", PLAYER, LobbyParticipantType.HUMAN);
        verify(openLobby, never()).open(any(LobbyCommand.OpenLobbyCommand.class));
    }

    private static Lobby lobby(String lobbyId, String initiatorId) {
        return Lobby.builder()
                .lobbyId(lobbyId)
                .topicId(TOPIC)
                .initiatorId(initiatorId)
                .status(LobbyStatus.OPEN)
                .createdAt(Instant.parse("2026-09-27T10:00:00Z"))
                .updatedAt(Instant.parse("2026-09-27T10:00:00Z"))
                .build();
    }
}
