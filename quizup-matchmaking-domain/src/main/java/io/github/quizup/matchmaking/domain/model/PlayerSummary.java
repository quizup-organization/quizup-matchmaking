package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

/**
 * Résumé d'un joueur pour le matchmaking (pseudonyme, niveau, XP totale, pays, langue) — type
 * local au module, résolu auprès de quizup-profile (profil + progression).
 */
public record PlayerSummary(
        String userId,
        String pseudonym,
        int level,
        int xpTotal,
        String country,
        Language language
) {
}
