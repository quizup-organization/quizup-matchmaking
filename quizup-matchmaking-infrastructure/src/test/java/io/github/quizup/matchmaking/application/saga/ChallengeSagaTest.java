package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.ChallengeRoomId;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

/**
 * Test in-memory de {@link ChallengeSaga} : la salle déterministe du défi produit la partie →
 * la saga marque le défi comme **abouti** (`StartChallengeCommand`) — c'est ce fait, et non la
 * simple acceptation, qui crédite le badge « Défis » du lanceur.
 */
class ChallengeSagaTest {

    private static final String CHALLENGE_ID = "challenge-1";
    private static final String TOPIC = "topic-1";
    private static final String CHALLENGER = "challenger-1";
    private static final String OPPONENT = "opponent-1";
    private static final String GAME_ID = "game-1";

    private final SagaTestFixture<ChallengeSaga> fixture =
            new SagaTestFixture<>(ChallengeSaga.class);

    @Test
    void accepted_thenRoomGameCreated_marksChallengeStarted() {
        fixture.givenAPublished(created())
                .andThenAPublished(accepted())
                .whenPublishingA(new RoomEvent.RoomGameCreatedEvent(
                        ChallengeRoomId.of(CHALLENGE_ID), GAME_ID, Instant.now()))
                // Le défi est marqué « abouti » : c'est ce fait, pas l'acceptation, qui crédite le
                // badge Défis du lanceur (cf. `ChallengeBadgesSaga`).
                .expectDispatchedCommands(
                        new ChallengeCommand.StartChallengeCommand(CHALLENGE_ID, GAME_ID));
    }

    @Test
    void rematchExpiresAtTheDeadlineCarriedByTheEvent() {
        Instant now = fixture.currentTime();
        ChallengeEvent.ChallengeCreatedEvent rematch =
                new ChallengeEvent.ChallengeCreatedEvent(
                        CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, GAME_ID,
                        now.plusSeconds(10), now);

        fixture.givenAPublished(rematch)
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectDispatchedCommands(
                        new ChallengeCommand.ExpireChallengeCommand(CHALLENGE_ID));
    }

    @Test
    void anotherRoomGameCreated_isNotRoutedToTheChallenge() {
        fixture.givenAPublished(created())
                .andThenAPublished(accepted())
                .whenPublishingA(new RoomEvent.RoomGameCreatedEvent("room-autre", GAME_ID, Instant.now()))
                .expectNoDispatchedCommands();
    }

    private static ChallengeEvent.ChallengeCreatedEvent created() {
        return new ChallengeEvent.ChallengeCreatedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, null,
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static ChallengeEvent.ChallengeAcceptedEvent accepted() {
        return new ChallengeEvent.ChallengeAcceptedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, Instant.now());
    }
}
