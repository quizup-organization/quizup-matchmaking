package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'un défi nominatif (intention asynchrone, distinct de la salle temps réel).
 * <p>
 * Un statut terminal ({@link #ACCEPTED}, {@link #DECLINED}, {@link #CANCELLED},
 * {@link #EXPIRED}) est purgé par la saga après rétention (deadline), puis {@code markDeleted}
 * (standard Axon).
 */
public enum ChallengeStatus {
    /** En attente de la réponse de l'invité. */
    PENDING,
    /** Accepté : la salle temps réel a été (ou va être) créée. */
    ACCEPTED,
    DECLINED,
    CANCELLED,
    EXPIRED
}
