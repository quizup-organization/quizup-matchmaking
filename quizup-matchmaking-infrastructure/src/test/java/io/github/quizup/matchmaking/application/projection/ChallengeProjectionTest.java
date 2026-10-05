package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.port.out.ChallengeRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChallengeProjectionTest {

    private static final Instant AT = Instant.parse("2026-10-05T10:00:00Z");

    private final ChallengeRepositoryPort repository = mock(ChallengeRepositoryPort.class);
    private final ChallengeProjection projection = new ChallengeProjection(repository);

    @Test
    void created_savesPendingChallenge() {
        projection.on(new ChallengeEvent.ChallengeCreatedEvent(
                "challenge-1", "topic-1", "challenger-1", "opponent-1", AT.plusSeconds(3600), AT));

        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(repository).save(captor.capture());
        Challenge saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(ChallengeStatus.PENDING);
        assertThat(saved.opponentId()).isEqualTo("opponent-1");
        assertThat(saved.resolvedAt()).isNull();
    }

    @Test
    void accepted_marksResolvedAndKeepsRoomLink() {
        when(repository.findById("challenge-1")).thenReturn(Optional.of(challenge()));

        projection.on(new ChallengeEvent.ChallengeAcceptedEvent(
                "challenge-1", "challenger-1", "opponent-1", AT));

        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ChallengeStatus.ACCEPTED);
        assertThat(captor.getValue().resolvedAt()).isEqualTo(AT);
    }

    @Test
    void roomCreated_linksRoom() {
        when(repository.findById("challenge-1")).thenReturn(Optional.of(challenge()));

        projection.on(new ChallengeEvent.ChallengeRoomCreatedEvent("challenge-1", "room-1", AT));

        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().roomId()).isEqualTo("room-1");
    }

    private static Challenge challenge() {
        return Challenge.builder()
                .challengeId("challenge-1")
                .topicId("topic-1")
                .challengerId("challenger-1")
                .opponentId("opponent-1")
                .status(ChallengeStatus.PENDING)
                .createdAt(AT)
                .expiresAt(AT.plusSeconds(3600))
                .build();
    }
}
