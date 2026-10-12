package io.github.quizup.matchmaking.domain.event;

import io.github.quizup.matchmaking.domain.model.TopicLanguageCoverage;
import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.time.Instant;
import java.util.Set;

public interface MatchmakingEvent {

    String matchmakingId();

    /** Le ticket porte sa photo de couverture linguistique : la saga compare localement. */
    record MatchmakingStartedEvent(
            String matchmakingId,
            String playerId,
            String topicId,
            int level,
            Set<Language> languages,
            TopicLanguageCoverage topicCoverage,
            Instant startedAt,
            Instant deadlineAt
    ) implements MatchmakingEvent {
    }

    record MatchmakingMatchedEvent(
            String matchmakingId,
            String opponentId,
            String roomId,
            boolean vsBot,
            Instant matchedAt
    ) implements MatchmakingEvent {
    }

    record MatchmakingCancelledEvent(
            String matchmakingId,
            String reason,
            Instant cancelledAt
    ) implements MatchmakingEvent {
    }

    record MatchmakingFailedEvent(
            String matchmakingId,
            String reason,
            Instant failedAt
    ) implements MatchmakingEvent {
    }

    /** Purge après rétention : l'agrégat est supprimé et la projection supprime sa ligne. */
    record MatchmakingPurgedEvent(
            String matchmakingId,
            Instant purgedAt
    ) implements MatchmakingEvent {
    }
}
