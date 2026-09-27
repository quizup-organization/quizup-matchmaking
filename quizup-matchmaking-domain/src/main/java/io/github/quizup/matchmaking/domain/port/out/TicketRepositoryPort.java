package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.MatchmakingTicket;

import java.util.Optional;

/**
 * Port sortant — persistance de la projection « ticket » (statuts explicites).
 */
public interface TicketRepositoryPort {

    void save(MatchmakingTicket ticket);

    Optional<MatchmakingTicket> findById(String ticketId);

    void deleteById(String ticketId);
}
