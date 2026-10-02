package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Test Axon in-memory de {@link MatchmakingAggregate} (recherche d'appariement public).
 */
class MatchmakingAggregateTest {

    private static final String MM_ID = "mm-1";
    private static final String PLAYER = "player-1";
    private static final String TOPIC = "topic-1";

    private final AggregateTestFixture<MatchmakingAggregate> fixture =
            new AggregateTestFixture<>(MatchmakingAggregate.class);

    @Test
    void create_appliesStartedEvent() {
        fixture.givenNoPriorActivity()
                .when(new MatchmakingCommand.CreateMatchmakingCommand(MM_ID, PLAYER, TOPIC, 10, Set.of(Language.FR)))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        MatchmakingEvent.MatchmakingStartedEvent.class,
                        e -> ((MatchmakingEvent.MatchmakingStartedEvent) e).matchmakingId().equals(MM_ID)));
    }

    @Test
    void match_appliesMatchedEvent() {
        fixture.given(started())
                .when(new MatchmakingCommand.MarkMatchmakingMatchedCommand(MM_ID, "opponent-1", "game-1", false))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        MatchmakingEvent.MatchmakingMatchedEvent.class,
                        e -> "game-1".equals(((MatchmakingEvent.MatchmakingMatchedEvent) e).gameId())));
    }

    @Test
    void matchIsIdempotentForSameGame() {
        fixture.given(started(), matched())
                .when(new MatchmakingCommand.MarkMatchmakingMatchedCommand(MM_ID, "opponent-1", "game-1", false))
                .expectNoEvents();
    }

    @Test
    void cancel_appliesCancelledEvent() {
        fixture.given(started())
                .when(new MatchmakingCommand.CancelMatchmakingCommand(MM_ID, PLAYER))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        MatchmakingEvent.MatchmakingCancelledEvent.class,
                        e -> ((MatchmakingEvent.MatchmakingCancelledEvent) e).matchmakingId().equals(MM_ID)));
    }

    @Test
    void fail_appliesFailedEvent() {
        fixture.given(started())
                .when(new MatchmakingCommand.FailMatchmakingCommand(MM_ID, "GAME_CREATION_FAILED"))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        MatchmakingEvent.MatchmakingFailedEvent.class,
                        e -> ((MatchmakingEvent.MatchmakingFailedEvent) e).matchmakingId().equals(MM_ID)));
    }

    private static MatchmakingEvent.MatchmakingStartedEvent started() {
        return new MatchmakingEvent.MatchmakingStartedEvent(
                MM_ID, PLAYER, TOPIC, 10, Set.of(Language.FR), java.time.Instant.now());
    }

    private static MatchmakingEvent.MatchmakingMatchedEvent matched() {
        return new MatchmakingEvent.MatchmakingMatchedEvent(
                MM_ID, "opponent-1", "game-1", false, java.time.Instant.now());
    }
}
