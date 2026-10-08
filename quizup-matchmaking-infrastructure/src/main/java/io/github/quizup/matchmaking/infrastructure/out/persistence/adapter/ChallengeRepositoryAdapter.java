package io.github.quizup.matchmaking.infrastructure.out.persistence.adapter;

import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.port.out.ChallengeRepositoryPort;
import io.github.quizup.matchmaking.infrastructure.out.persistence.mapper.ChallengeEntityMapper;
import io.github.quizup.matchmaking.infrastructure.out.persistence.repository.ChallengeJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ChallengeRepositoryAdapter implements ChallengeRepositoryPort {

    private final ChallengeJpaRepository challengeJpaRepository;

    public ChallengeRepositoryAdapter(ChallengeJpaRepository challengeJpaRepository) {
        this.challengeJpaRepository = challengeJpaRepository;
    }

    @Override
    @Transactional
    public void save(Challenge challenge) {
        challengeJpaRepository.save(ChallengeEntityMapper.toEntity(challenge));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Challenge> findById(String challengeId) {
        return challengeJpaRepository.findById(challengeId).map(ChallengeEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Challenge> findPendingByPlayerId(String playerId) {
        return challengeJpaRepository
                .findPendingByPlayerId(ChallengeStatus.PENDING, playerId)
                .stream()
                .map(ChallengeEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findTerminalIdsBefore(Instant before) {
        return challengeJpaRepository.findTerminalIdsBefore(ChallengeStatus.PENDING, before);
    }

    @Override
    @Transactional
    public void deleteById(String challengeId) {
        challengeJpaRepository.deleteById(challengeId);
    }
}
