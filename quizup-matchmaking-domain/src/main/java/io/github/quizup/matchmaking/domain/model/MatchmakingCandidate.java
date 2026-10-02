package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.time.Instant;
import java.util.Set;

/**
 * Candidat d'appariement extrait du pool de recherche (index dérivé, cf. {@code MatchmakingPoolPort}).
 */
public record MatchmakingCandidate(
        String matchmakingId,
        String playerId,
        String topicId,
        int level,
        Set<Language> languages,
        Instant createdAt
) {
}
