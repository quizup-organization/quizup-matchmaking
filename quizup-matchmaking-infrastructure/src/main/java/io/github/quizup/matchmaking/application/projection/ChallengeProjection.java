package io.github.quizup.matchmaking.application.projection;

import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.model.Challenge;
import io.github.quizup.matchmaking.domain.model.ChallengeRoomId;
import io.github.quizup.matchmaking.domain.model.ChallengeStatus;
import io.github.quizup.matchmaking.domain.port.out.ChallengeRepositoryPort;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/**
 * Projection read-only du défi nominatif (intention asynchrone) : réponse et lien vers la salle.
 */
@Component
@ProcessingGroup("challenge-projection")
public class ChallengeProjection {

    private final ChallengeRepositoryPort challengeRepositoryPort;

    public ChallengeProjection(ChallengeRepositoryPort challengeRepositoryPort) {
        this.challengeRepositoryPort = challengeRepositoryPort;
    }

    @EventHandler
    @Transactional
    public void on(ChallengeEvent.ChallengeCreatedEvent event) {
        challengeRepositoryPort.save(Challenge.builder()
                .challengeId(event.challengeId())
                .topicId(event.topicId())
                .challengerId(event.challengerId())
                .opponentId(event.opponentId())
                .status(ChallengeStatus.PENDING)
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(ChallengeEvent.ChallengeAcceptedEvent event) {
        update(event.challengeId(), challenge -> challenge.toBuilder()
                .status(ChallengeStatus.ACCEPTED)
                .resolvedAt(event.acceptedAt())
                .roomId(ChallengeRoomId.of(event.challengeId()))
                .build());
    }

    @EventHandler
    @Transactional
    public void on(ChallengeEvent.ChallengeDeclinedEvent event) {
        update(event.challengeId(), challenge -> challenge.toBuilder()
                .status(ChallengeStatus.DECLINED)
                .resolvedAt(event.declinedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(ChallengeEvent.ChallengeCancelledEvent event) {
        update(event.challengeId(), challenge -> challenge.toBuilder()
                .status(ChallengeStatus.CANCELLED)
                .resolvedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(ChallengeEvent.ChallengeExpiredEvent event) {
        update(event.challengeId(), challenge -> challenge.toBuilder()
                .status(ChallengeStatus.EXPIRED)
                .resolvedAt(event.expiredAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(ChallengeEvent.ChallengePurgedEvent event) {
        challengeRepositoryPort.deleteById(event.challengeId());
    }

    private void update(String challengeId, UnaryOperator<Challenge> transform) {
        challengeRepositoryPort.findById(challengeId).ifPresent(challenge ->
                challengeRepositoryPort.save(transform.apply(challenge)));
    }
}
