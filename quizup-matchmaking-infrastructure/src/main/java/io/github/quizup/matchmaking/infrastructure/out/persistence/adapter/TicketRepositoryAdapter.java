package io.github.quizup.matchmaking.infrastructure.out.persistence.adapter;

import io.github.quizup.matchmaking.domain.model.MatchmakingTicket;
import io.github.quizup.matchmaking.domain.port.out.TicketRepositoryPort;
import io.github.quizup.matchmaking.infrastructure.out.persistence.mapper.MatchmakingTicketEntityMapper;
import io.github.quizup.matchmaking.infrastructure.out.persistence.repository.MatchmakingTicketJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class TicketRepositoryAdapter implements TicketRepositoryPort {

    private final MatchmakingTicketJpaRepository repository;

    public TicketRepositoryAdapter(MatchmakingTicketJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(MatchmakingTicket ticket) {
        repository.save(MatchmakingTicketEntityMapper.toEntity(ticket));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MatchmakingTicket> findById(String ticketId) {
        return repository.findById(ticketId).map(MatchmakingTicketEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(String ticketId) {
        repository.deleteById(ticketId);
    }
}
