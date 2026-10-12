package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.util.List;
import java.util.Set;

/**
 * Photo de couverture linguistique d'un sujet, préparée par la façade à la création du ticket
 * (quizup-theme) puis portée par {@code MatchmakingStartedEvent} : la saga compare localement,
 * sans query bloquante — les événements restent auto-suffisants et le replay est déterministe.
 */
public record TopicLanguageCoverage(
        List<LanguageCoverage> combinations
) {

    /**
     * Nombre de questions approuvées disponibles dans <b>toutes</b> les langues de
     * {@code languages} (sémantique stricte du comptage theme).
     */
    public record LanguageCoverage(
            Set<Language> languages,
            int approvedCount
    ) {
    }

    public static TopicLanguageCoverage empty() {
        return new TopicLanguageCoverage(List.of());
    }

    public int approvedCountFor(Set<Language> languages) {
        if (combinations == null || languages == null || languages.isEmpty()) {
            return 0;
        }
        return combinations.stream()
                .filter(coverage -> languages.equals(coverage.languages()))
                .mapToInt(LanguageCoverage::approvedCount)
                .findFirst()
                .orElse(0);
    }
}
