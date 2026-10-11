package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.util.List;
import java.util.Set;

/**
 * Port sortant de tirage des questions d'une partie (quizup-theme). Le type retourné est
 * <b>local</b> au domaine matchmaking : un {@code *-domain} ne dépend jamais d'un autre service.
 * Implémenté par un adaptateur {@code application/service} qui interroge le bus.
 */
public interface QuestionDrawPort {

    /** Tire les questions approuvées disponibles dans toutes les langues demandées. */
    List<DrawnQuestion> draw(String topicId, Set<Language> languages);
}
