package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.theme.domain.query.QuestionQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Adaptateur sortant inter-module : vérifie auprès de quizup-theme que le thème contient assez
 * de questions approuvées disponibles dans toutes les langues requises.
 */
@Service
public class TopicAvailabilityService implements TopicAvailabilityPort {

    private final QueryGateway queryGateway;

    public TopicAvailabilityService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public boolean coversAllLanguages(String topicId, Set<Language> languages) {
        if (languages == null || languages.isEmpty()) {
            return false;
        }

        Integer count = queryGateway.query(
                new QuestionQuery.CountApprovedQuestionsByTopicAndLanguagesQuery(topicId, languages),
                QueryResponseTypes.instanceOf(Integer.class)
        ).join();

        return count != null && count >= GameRules.TOTAL_ROUNDS;
    }
}
