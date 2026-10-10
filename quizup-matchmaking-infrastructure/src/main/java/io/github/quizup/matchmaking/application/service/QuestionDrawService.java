package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.model.GameQuestion;
import io.github.quizup.game.domain.model.GameQuestionChoice;
import io.github.quizup.game.domain.model.GameQuestionContent;
import io.github.quizup.game.domain.model.GameRules;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.theme.domain.model.Question;
import io.github.quizup.theme.domain.model.QuestionChoice;
import io.github.quizup.theme.domain.model.QuestionContent;
import io.github.quizup.theme.domain.query.QuestionQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.isNull;

/**
 * Prépare les questions d'une partie (tirage aléatoire strict dans toutes les langues demandées)
 * et les mappe vers le modèle du domaine game.
 * <p>
 * Appelé par les sagas (fenêtre d'attente / appariement) et non par un handler de commande :
 * l'I/O inter-service ne doit jamais bloquer une action utilisateur.
 */
@Service
public class QuestionDrawService {

    private final QueryGateway queryGateway;

    public QuestionDrawService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /** Tire {@link GameRules#TOTAL_ROUNDS} questions approuvées disponibles dans toutes les langues. */
    public List<GameQuestion> draw(String topicId, Set<Language> languages) {
        if (languages == null || languages.isEmpty()) {
            return List.of();
        }
        List<Question> questions = queryGateway.query(
                new QuestionQuery.GetRandomApprovedQuestionsQuery(
                        topicId, GameRules.TOTAL_ROUNDS, languages),
                QueryResponseTypes.multipleInstancesOf(Question.class)
        ).join();
        return questions.stream().map(QuestionDrawService::toGameQuestion).toList();
    }

    // ── Mapping theme → game (dupliqué par émetteur : un *-domain ne dépend jamais d'un autre service) ──

    private static GameQuestion toGameQuestion(Question question) {
        Map<Language, GameQuestionContent> translations = new EnumMap<>(Language.class);
        if (question.contents() != null) {
            for (Map.Entry<Language, QuestionContent> content : question.contents().entrySet()) {
                translations.put(content.getKey(), toGameQuestionContent(content.getValue()));
            }
        }
        return new GameQuestion(
                question.questionId(),
                translations,
                question.imageUrl(),
                question.difficulty() != null ? question.difficulty().name() : null,
                toGameQuestionChoice(question.correctAnswer()));
    }

    private static GameQuestionContent toGameQuestionContent(QuestionContent content) {
        return new GameQuestionContent(content.text(), toGameQuestionChoices(content.answers()));
    }

    private static GameQuestionChoice toGameQuestionChoice(QuestionChoice questionChoice) {
        return isNull(questionChoice) ? null : GameQuestionChoice.valueOf(questionChoice.name());
    }

    private static Map<GameQuestionChoice, String> toGameQuestionChoices(Map<QuestionChoice, String> questionChoices) {
        Map<GameQuestionChoice, String> gameQuestionChoices = new EnumMap<>(GameQuestionChoice.class);
        if (isNull(questionChoices) || questionChoices.isEmpty()) {
            return gameQuestionChoices;
        }
        for (Map.Entry<QuestionChoice, String> entry : questionChoices.entrySet()) {
            GameQuestionChoice choice = toGameQuestionChoice(entry.getKey());
            if (!isNull(choice)) {
                gameQuestionChoices.put(choice, entry.getValue());
            }
        }
        return gameQuestionChoices;
    }
}
