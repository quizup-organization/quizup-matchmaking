package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopicLanguageCoverageTest {

    private final TopicLanguageCoverage coverage = new TopicLanguageCoverage(List.of(
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.FR), 10),
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.EN), 8),
            new TopicLanguageCoverage.LanguageCoverage(Set.of(Language.FR, Language.EN), 7)));

    @Test
    void approvedCountFor_returnsExactCombinationCount() {
        assertEquals(10, coverage.approvedCountFor(Set.of(Language.FR)));
        assertEquals(8, coverage.approvedCountFor(Set.of(Language.EN)));
        assertEquals(7, coverage.approvedCountFor(Set.of(Language.FR, Language.EN)));
    }

    @Test
    void approvedCountFor_unknownOrEmptyCombination_isZero() {
        assertEquals(0, coverage.approvedCountFor(Set.of()));
        assertEquals(0, coverage.approvedCountFor(null));
        assertEquals(0, TopicLanguageCoverage.empty().approvedCountFor(Set.of(Language.FR)));
    }
}
