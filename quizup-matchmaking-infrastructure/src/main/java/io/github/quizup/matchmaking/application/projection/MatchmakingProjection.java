package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.Matchmaking;
import io.github.quizup.matchmaking.domain.model.MatchmakingStatus;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingRepositoryPort;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model « ticket » d'appariement (vue produit) ; sert aussi d'index au pool
 * ({@code status = SEARCHING}, {@code claimed_by} null).
 */
@Component
@ProcessingGroup("matchmaking-projection")
public class MatchmakingProjection {

    private final MatchmakingRepositoryPort repository;

    public MatchmakingProjection(MatchmakingRepositoryPort repository) {
        this.repository = repository;
    }

    @EventHandler
    @Transactional
    public void on(MatchmakingEvent.MatchmakingStartedEvent event) {
        repository.save(Matchmaking.builder()
                .matchmakingId(event.matchmakingId())
                .playerId(event.playerId())
                .topicId(event.topicId())
                .level(event.level())
                .languages(event.languages())
                .vsBot(false)
                .status(MatchmakingStatus.SEARCHING)
                .createdAt(event.startedAt())
                .updatedAt(event.startedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(MatchmakingEvent.MatchmakingMatchedEvent event) {
        update(event.matchmakingId(), matchmaking -> matchmaking.toBuilder()
                .status(MatchmakingStatus.MATCHED)
                .opponentId(event.opponentId())
                .gameId(event.gameId())
                .vsBot(event.vsBot())
                .updatedAt(event.matchedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(MatchmakingEvent.MatchmakingCancelledEvent event) {
        update(event.matchmakingId(), matchmaking -> matchmaking.toBuilder()
                .status(MatchmakingStatus.CANCELLED)
                .updatedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(MatchmakingEvent.MatchmakingFailedEvent event) {
        update(event.matchmakingId(), matchmaking -> matchmaking.toBuilder()
                .status(MatchmakingStatus.FAILED)
                .updatedAt(event.failedAt())
                .build());
    }

    private void update(String matchmakingId, java.util.function.UnaryOperator<Matchmaking> transform) {
        repository.findById(matchmakingId).ifPresent(matchmaking ->
                repository.save(transform.apply(matchmaking)));
    }
}
