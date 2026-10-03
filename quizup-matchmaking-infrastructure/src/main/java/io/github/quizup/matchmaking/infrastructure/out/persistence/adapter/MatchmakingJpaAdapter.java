package io.github.quizup.matchmaking.infrastructure.out.persistence.adapter;

import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.model.MatchmakingCandidate;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPoolPort;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingRepositoryPort;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.MatchmakingEntity;
import io.github.quizup.matchmaking.infrastructure.out.persistence.mapper.MatchmakingEntityMapper;
import io.github.quizup.matchmaking.infrastructure.out.persistence.repository.MatchmakingJpaRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptateur JPA du pool d'appariement et du read model « ticket ». Le pool correspond aux
 * recherches {@code SEARCHING} non réclamées de la table.
 */
@Component
public class MatchmakingJpaAdapter implements MatchmakingPoolPort, MatchmakingRepositoryPort {

    private final MatchmakingJpaRepository repository;

    public MatchmakingJpaAdapter(MatchmakingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchmakingCandidate> candidates(String topicId, int level, int levelWindow,
                                                 String excludePlayerId, int limit) {
        return repository
                .findCandidates(topicId, excludePlayerId, level, level - levelWindow, level + levelWindow, Limit.of(limit))
                .stream()
                .filter(entity -> entity.getClaimedBy() == null)
                .map(MatchmakingEntityMapper::toCandidate)
                .toList();
    }

    @Override
    @Transactional
    public Optional<MatchmakingCandidate> claim(String matchmakingId, String claimedByPlayerId) {
        int updated = repository.claim(matchmakingId, claimedByPlayerId, Instant.now());
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findById(matchmakingId).map(MatchmakingEntityMapper::toCandidate);
    }

    @Override
    @Transactional
    public void save(Matchmaking matchmaking) {
        MatchmakingEntity entity = repository.findById(matchmaking.matchmakingId())
                .orElseGet(MatchmakingEntity::new);
        entity.setMatchmakingId(matchmaking.matchmakingId());
        entity.setPlayerId(matchmaking.playerId());
        entity.setTopicId(matchmaking.topicId());
        entity.setLevel(matchmaking.level());
        entity.setLanguages(MatchmakingEntityMapper.encodeLanguages(matchmaking.languages()));
        entity.setOpponentId(matchmaking.opponentId());
        entity.setGameId(matchmaking.gameId());
        entity.setVsBot(matchmaking.vsBot());
        entity.setStatus(matchmaking.status());
        entity.setCreatedAt(matchmaking.createdAt());
        entity.setUpdatedAt(matchmaking.updatedAt());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Matchmaking> findById(String matchmakingId) {
        return repository.findById(matchmakingId).map(MatchmakingEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(String matchmakingId) {
        repository.deleteById(matchmakingId);
    }
}
