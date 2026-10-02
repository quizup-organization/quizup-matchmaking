package io.github.quizup.matchmaking.domain.event;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.time.Instant;
import java.util.Set;

public interface MatchmakingEvent {

    String matchmakingId();

    record MatchmakingStartedEvent(
            String matchmakingId,
            String playerId,
            String topicId,
            int level,
            Set<Language> languages,
            Instant startedAt
    ) implements MatchmakingEvent {
    }

    record MatchmakingMatchedEvent(
            String matchmakingId,
            String opponentId,
            String gameId,
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
}
