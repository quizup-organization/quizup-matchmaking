package io.github.quizup.matchmaking.application.handler.event;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingMetricsPort;
import org.axonframework.eventhandling.DisallowReplay;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alimente les KPI métier du matchmaking à partir des événements de lobby.
 *
 * <p>Le temps d'attente est calculé par corrélation {@code LobbyOpenedEvent.openedAt} →
 * {@code LobbyCompletedEvent.closedAt}, via un cache mémoire borné (pas d'accès read-model).
 * Les handlers sont {@link DisallowReplay} pour ne pas réincrémenter sur replay.
 */
@Component
public class LobbyMetricsEventHandler {

    private static final int MAX_TRACKED_LOBBIES = 10_000;

    private final MatchmakingMetricsPort metrics;

    /** Cache borné lobbyId -> instant d'ouverture (évince les plus anciens). */
    private final Map<String, Instant> openedAt = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
                    return size() > MAX_TRACKED_LOBBIES;
                }
            });

    public LobbyMetricsEventHandler(MatchmakingMetricsPort metrics) {
        this.metrics = metrics;
    }

    @EventHandler
    @DisallowReplay
    public void on(LobbyEvent.LobbyOpenedEvent event) {
        openedAt.put(event.lobbyId(), event.openedAt());
        metrics.lobbyOpened(event.topicId());
    }

    @EventHandler
    @DisallowReplay
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        metrics.lobbyJoined(event.challengerType() == null ? "unknown" : event.challengerType().name());
    }

    @EventHandler
    @DisallowReplay
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        openedAt.remove(event.lobbyId());
        metrics.lobbyCancelled();
    }

    @EventHandler
    @DisallowReplay
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        Instant opened = openedAt.remove(event.lobbyId());
        long waitMs = opened != null ? Duration.between(opened, event.closedAt()).toMillis() : -1L;
        metrics.matchFound(event.topicId(), event.vsBot(), waitMs);
    }

    @EventHandler
    @DisallowReplay
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        openedAt.remove(event.lobbyId());
        metrics.lobbyPurged();
    }
}
