package io.github.quizup.matchmaking.domain.model;

import lombok.Builder;

import java.time.Instant;

/**
 * Read model d'un défi nominatif : intention asynchrone « A défie B », sans présence ni temps réel.
 * Une fois accepté, il est relié à la salle (`roomId`) qui porte la présence et le lancement.
 */
@Builder(toBuilder = true)
public record Challenge(
        String challengeId,
        String topicId,
        String challengerId,
        String opponentId,
        String roomId,
        ChallengeStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant resolvedAt
) {
}
