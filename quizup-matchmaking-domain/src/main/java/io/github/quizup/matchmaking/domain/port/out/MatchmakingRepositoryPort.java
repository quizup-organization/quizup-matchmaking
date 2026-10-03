package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.Matchmaking;

import java.util.Optional;

/**
 * Port sortant — read model « ticket » d'appariement (vue produit).
 */
public interface MatchmakingRepositoryPort {

    void save(Matchmaking matchmaking);

    Optional<Matchmaking> findById(String matchmakingId);

    void deleteById(String matchmakingId);
}
