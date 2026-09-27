package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.model.MatchmakingTicket;
import io.github.quizup.matchmaking.domain.port.out.TicketRepositoryPort;
import io.github.quizup.matchmaking.domain.query.TicketQuery;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handler Axon — lecture du read model « ticket ».
 */
@Component
public class TicketQueryHandler {

    private final TicketRepositoryPort ticketRepositoryPort;

    public TicketQueryHandler(TicketRepositoryPort ticketRepositoryPort) {
        this.ticketRepositoryPort = ticketRepositoryPort;
    }

    @QueryHandler
    public Optional<MatchmakingTicket> handle(TicketQuery.FindTicketByIdQuery query) {
        return ticketRepositoryPort.findById(query.ticketId());
    }
}
