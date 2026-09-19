package io.github.quizup.matchmaking.domain.model;

/**
 * Résumé d'un joueur pour le matchmaking (nom, niveau, pays) — type local au
 * module, résolu auprès de quizup-profile / quizup-profile (progression).
 */
public record PlayerSummary(
        String userId,
        String displayName,
        int level,
        String country
) {
}
