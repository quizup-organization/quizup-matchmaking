package io.github.quizup.matchmaking.infrastructure.out.metrics.adapter;

import io.github.quizup.matchmaking.domain.port.out.MatchmakingMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Adapter Micrometer du {@link MatchmakingMetricsPort}.
 * <p>Tags communs {@code application}/{@code environment}/{@code version} ajoutés par le SDK.
 */
@Component
public class LobbyMetricsAdapter implements MatchmakingMetricsPort {

    private final MeterRegistry registry;

    public LobbyMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void lobbyOpened(String topicId) {
        Counter.builder("quizup.matchmaking.lobbies.opened")
                .tag("topic", safe(topicId))
                .register(registry)
                .increment();
    }

    @Override
    public void lobbyJoined(String challengerType) {
        Counter.builder("quizup.matchmaking.lobbies.joined")
                .tag("challenger_type", safe(challengerType))
                .register(registry)
                .increment();
    }

    @Override
    public void lobbyCancelled() {
        Counter.builder("quizup.matchmaking.lobbies.cancelled")
                .register(registry)
                .increment();
    }

    @Override
    public void matchFound(String topicId, boolean vsBot, long waitMs) {
        Counter.builder("quizup.matchmaking.matches")
                .tag("topic", safe(topicId))
                .tag("vs_bot", Boolean.toString(vsBot))
                .register(registry)
                .increment();

        if (waitMs >= 0) {
            Timer.builder("quizup.matchmaking.match.wait")
                    .tag("vs_bot", Boolean.toString(vsBot))
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.ofMillis(waitMs));
        }
    }

    @Override
    public void lobbyPurged() {
        Counter.builder("quizup.matchmaking.lobbies.purged")
                .register(registry)
                .increment();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
