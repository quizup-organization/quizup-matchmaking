package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

/**
 * Read model d'une recherche d'appariement (vue produit), consommé par le BFF.
 */
@Builder(toBuilder = true)
public record Matchmaking(
        String matchmakingId,
        String playerId,
        String topicId,
        int level,
        Set<Language> languages,
        String opponentId,
        String gameId,
        boolean vsBot,
        MatchmakingStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
