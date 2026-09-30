package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.util.Set;

/**
 * Vérifie qu'un thème peut servir une partie dans **toutes** les langues requises
 * (assez de questions approuvées localisées pour un duel complet).
 */
public interface TopicAvailabilityPort {

    boolean coversAllLanguages(String topicId, Set<Language> languages);
}
