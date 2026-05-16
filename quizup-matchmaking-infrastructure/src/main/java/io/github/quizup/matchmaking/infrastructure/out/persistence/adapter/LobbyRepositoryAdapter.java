package io.github.quizup.matchmaking.infrastructure.out.persistence.adapter;

import io.github.quizup.common.domain.model.search.PageResult;
import io.github.quizup.common.domain.model.search.SearchCriteria;
import io.github.quizup.common.infrastructure.adapter.AnnotationSearchableEntity;
import io.github.quizup.common.infrastructure.adapter.JpaSearchAdapter;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.LobbyEntity;
import io.github.quizup.matchmaking.infrastructure.out.persistence.mapper.LobbyEntityMapper;
import io.github.quizup.matchmaking.infrastructure.out.persistence.repository.LobbyJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class LobbyRepositoryAdapter implements LobbyRepositoryPort {

    private final LobbyJpaRepository lobbyJpaRepository;
    private final JpaSearchAdapter<LobbyEntity> lobbyJpaSearchAdapter;

    public LobbyRepositoryAdapter(LobbyJpaRepository lobbyJpaRepository) {
        this.lobbyJpaRepository = lobbyJpaRepository;
        this.lobbyJpaSearchAdapter = new JpaSearchAdapter<>(
                lobbyJpaRepository,
                new AnnotationSearchableEntity(LobbyEntity.class)
        );
    }

    @Override
    @Transactional
    public void save(Lobby lobby) {
        lobbyJpaRepository.save(LobbyEntityMapper.toEntity(lobby));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Lobby> findById(String lobbyId) {
        return lobbyJpaRepository.findById(lobbyId).map(LobbyEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Lobby> findFirstOpenByTopicId(String topicId) {
        return lobbyJpaRepository.findFirstByTopicIdAndStatusOrderByCreatedAtAsc(topicId, LobbyStatus.OPEN)
                .map(LobbyEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lobby> findOpenByTopicId(String topicId) {
        return lobbyJpaRepository.findByTopicIdAndStatusOrderByCreatedAtAsc(topicId, LobbyStatus.OPEN)
                .stream()
                .map(LobbyEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Lobby> findAll(SearchCriteria searchCriteria) {
        return lobbyJpaSearchAdapter.findAll(searchCriteria)
                .map(LobbyEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(String lobbyId) {
        lobbyJpaRepository.deleteById(lobbyId);
    }
}

