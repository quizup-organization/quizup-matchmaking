package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.axon.test.QuizUpAxonMatchers;
import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.exception.ChallengeExceptions;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * Test Axon in-memory de {@link ChallengeAggregate} : intention asynchrone A → B,
 * sans présence ni création de partie.
 */
class ChallengeAggregateTest {

    private static final String CHALLENGE_ID = "challenge-1";
    private static final String TOPIC = "topic-1";
    private static final String CHALLENGER = "challenger-1";
    private static final String OPPONENT = "opponent-1";
    private static final String OTHER = "other-1";

    private final AggregateTestFixture<ChallengeAggregate> fixture =
            new AggregateTestFixture<>(ChallengeAggregate.class);

    ChallengeAggregateTest() {
        fixture.registerInjectableResource((ProfileRepositoryPort) id -> null);
        fixture.registerInjectableResource((TopicAvailabilityPort) (topicId, languages) -> true);
    }

    @Test
    void create_appliesCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeCreatedEvent.class,
                        e -> CHALLENGE_ID.equals(((ChallengeEvent.ChallengeCreatedEvent) e).challengeId())
                                && OPPONENT.equals(((ChallengeEvent.ChallengeCreatedEvent) e).opponentId())));
    }

    @Test
    void cannotChallengeSelf() {
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, CHALLENGER))
                .expectException(ChallengeExceptions.CannotChallengeSelfProblem.class);
    }

    @Test
    void accept_byOpponent_appliesAccepted() {
        fixture.given(created())
                .when(new ChallengeCommand.AcceptChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeAcceptedEvent.class,
                        e -> OPPONENT.equals(((ChallengeEvent.ChallengeAcceptedEvent) e).opponentId())));
    }

    @Test
    void accept_twiceIsIdempotent() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.AcceptChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectNoEvents();
    }

    @Test
    void accept_refusedToNonInvitedPlayer() {
        fixture.given(created())
                .when(new ChallengeCommand.AcceptChallengeCommand(CHALLENGE_ID, OTHER))
                .expectException(ChallengeExceptions.ChallengeNotInvitedProblem.class);
    }

    @Test
    void decline_byOpponent_appliesDeclined() {
        fixture.given(created())
                .when(new ChallengeCommand.DeclineChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeDeclinedEvent.class,
                        e -> OPPONENT.equals(((ChallengeEvent.ChallengeDeclinedEvent) e).opponentId())));
    }

    @Test
    void cancel_byChallenger_appliesCancelled() {
        fixture.given(created())
                .when(new ChallengeCommand.CancelChallengeCommand(CHALLENGE_ID, CHALLENGER))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeCancelledEvent.class,
                        e -> CHALLENGER.equals(((ChallengeEvent.ChallengeCancelledEvent) e).challengerId())));
    }

    @Test
    void cancel_refusedToOpponent() {
        fixture.given(created())
                .when(new ChallengeCommand.CancelChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectException(ChallengeExceptions.PlayerNotChallengerProblem.class);
    }

    @Test
    void expire_appliesExpired() {
        fixture.given(created())
                .when(new ChallengeCommand.ExpireChallengeCommand(CHALLENGE_ID))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeExpiredEvent.class, e -> true));
    }

    @Test
    void expire_isIgnoredWhenAlreadyResolved() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.ExpireChallengeCommand(CHALLENGE_ID))
                .expectNoEvents();
    }

    @Test
    void purge_afterTerminal_appliesPurgedAndDeletesAggregate() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.PurgeChallengeCommand(CHALLENGE_ID))
                .expectEventsMatching(QuizUpAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengePurgedEvent.class,
                        e -> CHALLENGE_ID.equals(((ChallengeEvent.ChallengePurgedEvent) e).challengeId())))
                .expectMarkedDeleted();
    }

    private static ChallengeEvent.ChallengeCreatedEvent created() {
        return new ChallengeEvent.ChallengeCreatedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT,
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static ChallengeEvent.ChallengeAcceptedEvent accepted() {
        return new ChallengeEvent.ChallengeAcceptedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, Instant.now());
    }
}
