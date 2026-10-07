package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.model.Challenge;

import java.util.List;
import java.util.Optional;

/**
 * Store chaud (Redis) des défis nominatifs : intention asynchrone avec TTL (1 h), résolue par
 * acceptation/refus/annulation/expiration. À l'acceptation, la salle temps réel est créée et
 * reliée au défi ({@code roomId}) ; chaque transition écrit son événement d'outbox atomiquement.
 */
public interface ChallengeStorePort {

    void create(Challenge challenge, ChallengeEvent.ChallengeCreatedEvent event);

    Optional<Challenge> findChallengeById(String challengeId);

    /** Défis encore {@code PENDING} où le joueur est challenger ou opposant. */
    List<Challenge> findPendingByPlayerId(String playerId);

    AcceptOutcome accept(String challengeId, String playerId, ChallengeEvent.ChallengeAcceptedEvent event);

    boolean linkRoom(String challengeId, String roomId, ChallengeEvent.ChallengeRoomCreatedEvent event);

    boolean decline(String challengeId, ChallengeEvent.ChallengeDeclinedEvent event);

    boolean cancel(String challengeId, ChallengeEvent.ChallengeCancelledEvent event);

    boolean expire(String challengeId, ChallengeEvent.ChallengeExpiredEvent event);

    enum AcceptOutcome {
        ACCEPTED, IDEMPOTENT, NOT_PENDING, NOT_OPPONENT
    }
}
