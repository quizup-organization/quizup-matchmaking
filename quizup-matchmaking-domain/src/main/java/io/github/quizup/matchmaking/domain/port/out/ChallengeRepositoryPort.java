package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.Challenge;

import java.util.List;
import java.util.Optional;

/** Port sortant du read model des défis nominatifs. */
public interface ChallengeRepositoryPort {

    void save(Challenge challenge);

    Optional<Challenge> findById(String challengeId);

    /** Défis encore en attente de réponse où le joueur est lanceur ou invité. */
    List<Challenge> findPendingByPlayerId(String playerId);
}
