package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.model.MatchmakingTicket;
import io.github.quizup.matchmaking.domain.model.MatchmakingTicketStatus;
import io.github.quizup.matchmaking.domain.port.out.TicketRepositoryPort;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/**
 * Projette les événements de lobby en **tickets** (statuts produit explicites) : c'est le read
 * model consommé par le BFF, qui n'interprète plus le statut interne du lobby.
 */
@Component
@ProcessingGroup("matchmaking-ticket-projection")
public class MatchmakingTicketProjection {

    private final TicketRepositoryPort ticketRepositoryPort;

    public MatchmakingTicketProjection(TicketRepositoryPort ticketRepositoryPort) {
        this.ticketRepositoryPort = ticketRepositoryPort;
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyOpenedEvent event) {
        ticketRepositoryPort.save(MatchmakingTicket.builder()
                .ticketId(event.lobbyId())
                .topicId(event.topicId())
                .initiatorId(event.initiatorId())
                .status(MatchmakingTicketStatus.SEARCHING)
                .createdAt(event.openedAt())
                .updatedAt(event.openedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        update(event.lobbyId(), ticket -> ticket.toBuilder()
                .opponentId(event.challengerId())
                .vsBot(LobbyParticipantType.BOT.equals(event.challengerType()))
                .updatedAt(event.joinedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        update(event.lobbyId(), ticket -> ticket.toBuilder()
                .status(MatchmakingTicketStatus.MATCHED)
                .opponentId(event.challengerId())
                .gameId(event.gameId())
                .vsBot(event.vsBot())
                .updatedAt(event.closedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        update(event.lobbyId(), ticket -> ticket.toBuilder()
                .status(MatchmakingTicketStatus.CANCELLED)
                .updatedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        ticketRepositoryPort.deleteById(event.lobbyId());
    }

    private void update(String ticketId, UnaryOperator<MatchmakingTicket> transform) {
        ticketRepositoryPort.findById(ticketId).ifPresent(ticket ->
                ticketRepositoryPort.save(transform.apply(ticket)));
    }
}
