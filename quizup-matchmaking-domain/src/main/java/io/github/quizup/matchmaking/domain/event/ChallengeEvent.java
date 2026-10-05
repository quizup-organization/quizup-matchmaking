package io.github.quizup.matchmaking.domain.event;

import java.time.Instant;

/**
 * Événements du défi nominatif (intention asynchrone). La salle temps réel (présence, ready check,
 * création de partie) porte ses propres événements.
 */
public interface ChallengeEvent {

    String challengeId();

    record ChallengeCreatedEvent(
            String challengeId,
            String topicId,
            String challengerId,
            String opponentId,
            Instant expiresAt,
            Instant createdAt
    ) implements ChallengeEvent {
    }

    record ChallengeAcceptedEvent(
            String challengeId,
            String challengerId,
            String opponentId,
            Instant acceptedAt
    ) implements ChallengeEvent {
    }

    record ChallengeDeclinedEvent(
            String challengeId,
            String challengerId,
            String opponentId,
            Instant declinedAt
    ) implements ChallengeEvent {
    }

    record ChallengeCancelledEvent(
            String challengeId,
            String challengerId,
            Instant cancelledAt
    ) implements ChallengeEvent {
    }

    record ChallengeExpiredEvent(
            String challengeId,
            Instant expiredAt
    ) implements ChallengeEvent {
    }

    /** La salle temps réel a été créée à l'acceptation (lien durable pour la reprise). */
    record ChallengeRoomCreatedEvent(
            String challengeId,
            String roomId,
            Instant roomCreatedAt
    ) implements ChallengeEvent {
    }
}
