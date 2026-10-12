package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.matchmaking.domain.command.MatchmakingCommand;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.MatchmakingCandidate;
import io.github.quizup.matchmaking.domain.model.MatchmakingDeadline;
import io.github.quizup.matchmaking.domain.model.TopicLanguageCoverage;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPoolPort;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.SagaTestFixture;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;

/**
 * Test in-memory de {@link MatchmakingSaga} via {@link SagaTestFixture} : la couverture
 * linguistique est évaluée localement depuis l'événement de démarrage (aucune query bloquante),
 * le repli bot reste déclenché par deadline.
 */
class MatchmakingSagaTest {

    private static final String MM_ID = "mm-1";
    private static final String PLAYER = "player-1";
    private static final String TOPIC = "topic-1";
    private static final String OPPONENT = "opponent-1";

    private static final TopicLanguageCoverage FULL_COVERAGE = new TopicLanguageCoverage(List.of(
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.FR), 10),
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.EN), 8),
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.FR, Language.EN), 7)));

    private static final TopicLanguageCoverage PARTIAL_COVERAGE = new TopicLanguageCoverage(List.of(
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.FR), 10),
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.EN), 8)));

    private final MatchmakingPoolPort pool = Mockito.mock(MatchmakingPoolPort.class);

    private final SagaTestFixture<MatchmakingSaga> fixture = new SagaTestFixture<>(MatchmakingSaga.class);

    MatchmakingSagaTest() {
        fixture.registerResource(pool);
    }

    @Test
    void started_withCoveredCandidate_createsRoomAndMarksBothTickets() {
        MatchmakingCandidate candidate = new MatchmakingCandidate(
                "mm-old", OPPONENT, Set.of(Language.EN), Instant.now().minusSeconds(30));
        when(pool.candidates(TOPIC, 10, 5, PLAYER, 10)).thenReturn(List.of(candidate));
        when(pool.claim("mm-old", PLAYER)).thenReturn(Optional.of(candidate));

        fixture.givenNoPriorActivity()
                .whenPublishingA(started(FULL_COVERAGE))
                .expectDispatchedCommandsMatching(humanMatchMatching());
    }

    @Test
    void started_withUncoveredLanguageUnion_doesNotMatch() {
        MatchmakingCandidate candidate = new MatchmakingCandidate(
                "mm-old", OPPONENT, Set.of(Language.EN), Instant.now().minusSeconds(30));
        when(pool.candidates(TOPIC, 10, 5, PLAYER, 10)).thenReturn(List.of(candidate));

        fixture.givenNoPriorActivity()
                .whenPublishingA(started(PARTIAL_COVERAGE))
                .expectNoDispatchedCommands();
    }

    @Test
    void deadline_withoutCandidate_createsBotRoom() {
        when(pool.candidates(TOPIC, 10, 5, PLAYER, 10)).thenReturn(List.of());

        fixture.givenNoPriorActivity()
                .whenPublishingA(started(FULL_COVERAGE))
                .expectNoDispatchedCommands();

        fixture.whenTimeElapses(MatchmakingDeadline.MATCHMAKING_DEADLINE_DURATION)
                .expectDispatchedCommandsMatching(botRoomMatching());
    }

    private static MatchmakingEvent.MatchmakingStartedEvent started(TopicLanguageCoverage coverage) {
        Instant now = Instant.now();
        return new MatchmakingEvent.MatchmakingStartedEvent(
                MM_ID, PLAYER, TOPIC, 10, Set.of(Language.FR), coverage, now, now.plusSeconds(5));
    }

    private static Matcher<? extends List<? super CommandMessage<?>>> humanMatchMatching() {
        return Matchers.matches(commands -> commands.size() == 3
                && commands.stream().anyMatch(command -> command instanceof CommandMessage<?> message
                        && message.getPayload() instanceof RoomCommand.CreateRoomCommand createRoom
                        && TOPIC.equals(createRoom.topicId())
                        && PLAYER.equals(createRoom.initiatorId())
                        && OPPONENT.equals(createRoom.opponentId()))
                && commands.stream().filter(command -> command instanceof CommandMessage<?> message
                        && message.getPayload() instanceof MatchmakingCommand.MarkMatchmakingMatchedCommand)
                .count() == 2);
    }

    private static Matcher<? extends List<? super CommandMessage<?>>> botRoomMatching() {
        return Matchers.matches(commands -> commands.size() == 2
                && commands.stream().anyMatch(command -> command instanceof CommandMessage<?> message
                        && message.getPayload() instanceof RoomCommand.CreateRoomCommand createRoom
                        && createRoom.vsBot()
                        && QuizUpConstants.SYSTEM_USER_ID.equals(createRoom.opponentId()))
                && commands.stream().anyMatch(command -> command instanceof CommandMessage<?> message
                        && message.getPayload() instanceof MatchmakingCommand.MarkMatchmakingMatchedCommand mark
                        && mark.vsBot()));
    }
}
