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
            String topicId,
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

    /**
     * Ancien lien salle porté par l'agrégat : conservé uniquement pour la désérialisation des
     * streams historiques (event store / bus). Le {@code roomId} est désormais dérivé
     * ({@code ChallengeRoomId}) et posé par la projection sur {@link ChallengeAcceptedEvent}.
     *
     * @deprecated n'est plus émis ; le roomId est déterministe.
     */
    @Deprecated(forRemoval = false)
    record ChallengeRoomCreatedEvent(
            String challengeId,
            String roomId,
            Instant roomCreatedAt
    ) implements ChallengeEvent {
    }

    /** Purge après rétention : l'agrégat est supprimé et la projection supprime sa ligne. */
    record ChallengePurgedEvent(
            String challengeId,
            Instant purgedAt
    ) implements ChallengeEvent {
    }
}
