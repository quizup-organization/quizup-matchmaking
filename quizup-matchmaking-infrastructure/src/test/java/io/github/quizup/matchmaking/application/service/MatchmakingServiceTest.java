package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingStorePort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchmakingServiceTest {

    private static final String TOPIC = "topic-1";

    private InMemoryMatchmakingStore store;
    private CommandGateway commandGateway;
    private MatchmakingPlayerPort playerPort;
    private TopicAvailabilityPort topicAvailabilityPort;
    private MatchmakingService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryMatchmakingStore();
        commandGateway = mock(CommandGateway.class);
        when(commandGateway.sendAndWait(any())).thenReturn(null);
        playerPort = mock(MatchmakingPlayerPort.class);
        when(playerPort.getPlayer(any())).thenAnswer(invocation ->
                new PlayerSummary(invocation.getArgument(0), "player-" + invocation.getArgument(0), 10, null, Language.FR));
        topicAvailabilityPort = mock(TopicAvailabilityPort.class);
        when(topicAvailabilityPort.coversAllLanguages(any(), any())).thenReturn(true);
        service = new MatchmakingService(store, commandGateway, playerPort, topicAvailabilityPort);
    }

    @Test
    void create_enqueuesTicket() {
        service.create(createCommand("t1", "u1", 10));

        assertThat(store.findById("t1")).isPresent()
                .get().extracting(Matchmaking::status).isEqualTo(MatchmakingStatus.SEARCHING);
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void create_pairsWithOlderCompatibleCandidate_andCreatesGame() {
        enqueueCandidate("t0", "u0", 12, Instant.now().minusSeconds(2));

        service.create(createCommand("t1", "u1", 10));

        ArgumentCaptor<GameCommand.CreateGameCommand> game = ArgumentCaptor.forClass(GameCommand.CreateGameCommand.class);
        verify(commandGateway).sendAndWait(game.capture());
        assertThat(game.getValue().player1Id()).isEqualTo("u1");
        assertThat(game.getValue().player2Id()).isEqualTo("u0");
        assertThat(game.getValue().player2Type()).isEqualTo(GamePlayerType.HUMAN);

        Matchmaking mine = store.findById("t1").orElseThrow();
        Matchmaking candidate = store.findById("t0").orElseThrow();
        assertThat(mine.status()).isEqualTo(MatchmakingStatus.CLOSED);
        assertThat(mine.gameId()).isEqualTo(candidate.gameId());
        assertThat(mine.opponentId()).isEqualTo("u0");
        assertThat(candidate.opponentId()).isEqualTo("u1");
    }

    @Test
    void create_skipsCandidateOutsideLevelWindow() {
        enqueueCandidate("t0", "u0", 40, Instant.now().minusSeconds(2));

        service.create(createCommand("t1", "u1", 10));

        verify(commandGateway, never()).sendAndWait(any());
        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.SEARCHING);
    }

    @Test
    void create_skipsCandidateWhenTopicDoesNotCoverLanguages() {
        enqueueCandidate("t0", "u0", 10, Instant.now().minusSeconds(2));
        when(topicAvailabilityPort.coversAllLanguages(any(), any())).thenReturn(false);

        service.create(createCommand("t1", "u1", 10));

        verify(commandGateway, never()).sendAndWait(any());
        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.SEARCHING);
    }

    @Test
    void gameCreationFailure_marksBothTicketsFailed() {
        enqueueCandidate("t0", "u0", 10, Instant.now().minusSeconds(2));
        when(commandGateway.sendAndWait(any())).thenThrow(new IllegalStateException("boom"));

        service.create(createCommand("t1", "u1", 10));

        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.FAILED);
        assertThat(store.findById("t0")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.FAILED);
    }

    @Test
    void cancel_marksTicketCancelled_andRejectsOtherPlayers() {
        service.create(createCommand("t1", "u1", 10));

        assertThatThrownBy(() -> service.cancel(new MatchmakingCommand.CancelMatchmakingCommand("t1", "intruder")))
                .isInstanceOf(LobbyExceptions.PlayerNotInLobbyProblem.class);

        service.cancel(new MatchmakingCommand.CancelMatchmakingCommand("t1", "u1"));

        assertThat(store.findById("t1")).get().extracting(Matchmaking::status)
                .isEqualTo(MatchmakingStatus.CLOSED);
    }

    @Test
    void onDeadline_createsBotGame_whenStillSearching() {
        service.create(createCommand("t1", "u1", 10));

        service.onDeadline("t1");

        ArgumentCaptor<GameCommand.CreateGameCommand> game = ArgumentCaptor.forClass(GameCommand.CreateGameCommand.class);
        verify(commandGateway).sendAndWait(game.capture());
        assertThat(game.getValue().player2Type()).isEqualTo(GamePlayerType.BOT);

        Matchmaking ticket = store.findById("t1").orElseThrow();
        assertThat(ticket.vsBot()).isTrue();
        assertThat(ticket.gameId()).isNotBlank();
    }

    @Test
    void onDeadline_isNoOp_whenAlreadyMatched() {
        enqueueCandidate("t0", "u0", 10, Instant.now().minusSeconds(2));
        service.create(createCommand("t1", "u1", 10));
        verify(commandGateway).sendAndWait(any());

        service.onDeadline("t1");

        verify(commandGateway).sendAndWait(any());
    }

    private static MatchmakingCommand.CreateMatchmakingCommand createCommand(String ticketId, String playerId, int level) {
        return new MatchmakingCommand.CreateMatchmakingCommand(
                ticketId, playerId, TOPIC, level, Set.of(Language.FR));
    }

    private void enqueueCandidate(String ticketId, String playerId, int level, Instant createdAt) {
        Matchmaking ticket = Matchmaking.builder()
                .matchmakingId(ticketId)
                .playerId(playerId)
                .topicId(TOPIC)
                .level(level)
                .languages(Set.of(Language.FR))
                .vsBot(false)
                .status(MatchmakingStatus.SEARCHING)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        store.enqueue(ticket, new MatchmakingEvent.MatchmakingStartedEvent(
                ticketId, playerId, TOPIC, level, Set.of(Language.FR), createdAt));
    }

    private static final class InMemoryMatchmakingStore implements MatchmakingStorePort {

        private final Map<String, Matchmaking> tickets = new LinkedHashMap<>();

        @Override
        public void enqueue(Matchmaking ticket, MatchmakingEvent.MatchmakingStartedEvent event) {
            tickets.put(ticket.matchmakingId(), ticket);
        }

        @Override
        public Optional<Matchmaking> findById(String ticketId) {
            return Optional.ofNullable(tickets.get(ticketId));
        }

        @Override
        public List<Matchmaking> candidates(String topicId, Instant olderThan, int limit) {
            return tickets.values().stream()
                    .filter(ticket -> ticket.status() == MatchmakingStatus.SEARCHING)
                    .filter(ticket -> ticket.topicId().equals(topicId))
                    .filter(ticket -> ticket.createdAt().isBefore(olderThan))
                    .sorted(Comparator.comparing(Matchmaking::createdAt))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean claimPair(String firstTicketId, String secondTicketId) {
            Matchmaking first = tickets.get(firstTicketId);
            Matchmaking second = tickets.get(secondTicketId);
            if (first == null || second == null
                    || first.status() != MatchmakingStatus.SEARCHING
                    || second.status() != MatchmakingStatus.SEARCHING) {
                return false;
            }
            tickets.put(firstTicketId, first.toBuilder().status(MatchmakingStatus.CLOSED).build());
            tickets.put(secondTicketId, second.toBuilder().status(MatchmakingStatus.CLOSED).build());
            return true;
        }

        @Override
        public void completePair(String firstTicketId,
                                 String secondTicketId,
                                 MatchmakingEvent.MatchmakingMatchedEvent firstEvent,
                                 MatchmakingEvent.MatchmakingMatchedEvent secondEvent) {
            applyMatched(firstTicketId, firstEvent);
            applyMatched(secondTicketId, secondEvent);
        }

        @Override
        public boolean claimForBot(String ticketId) {
            Matchmaking ticket = tickets.get(ticketId);
            if (ticket == null || ticket.status() != MatchmakingStatus.SEARCHING) {
                return false;
            }
            tickets.put(ticketId, ticket.toBuilder().status(MatchmakingStatus.CLOSED).build());
            return true;
        }

        @Override
        public void completeBot(String ticketId, MatchmakingEvent.MatchmakingMatchedEvent event) {
            applyMatched(ticketId, event);
        }

        @Override
        public void fail(String ticketId, MatchmakingEvent.MatchmakingFailedEvent event) {
            Matchmaking ticket = tickets.get(ticketId);
            if (ticket != null) {
                tickets.put(ticketId, ticket.toBuilder().status(MatchmakingStatus.FAILED).build());
            }
        }

        @Override
        public boolean cancel(String ticketId, MatchmakingEvent.MatchmakingCancelledEvent event) {
            Matchmaking ticket = tickets.get(ticketId);
            if (ticket == null || ticket.status() != MatchmakingStatus.SEARCHING) {
                return false;
            }
            tickets.put(ticketId, ticket.toBuilder().status(MatchmakingStatus.CLOSED).build());
            return true;
        }

        private void applyMatched(String ticketId, MatchmakingEvent.MatchmakingMatchedEvent event) {
            Matchmaking ticket = tickets.get(ticketId);
            if (ticket != null) {
                tickets.put(ticketId, ticket.toBuilder()
                        .status(MatchmakingStatus.CLOSED)
                        .gameId(event.gameId())
                        .opponentId(event.opponentId())
                        .vsBot(event.vsBot())
                        .build());
            }
        }
    }
}
