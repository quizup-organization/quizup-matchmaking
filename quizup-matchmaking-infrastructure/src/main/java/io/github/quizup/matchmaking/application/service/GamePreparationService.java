package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.command.GameCommand;
import io.github.quizup.game.domain.model.BotDifficulty;
import io.github.quizup.game.domain.model.GamePlayerType;
import io.github.quizup.game.domain.model.GameQuestion;
import io.github.quizup.game.domain.model.GameQuestionChoice;
import io.github.quizup.game.domain.model.GameQuestionContent;
import io.github.quizup.game.domain.model.PlayerProgressSnapshot;
import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionChoice;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionContent;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assemblage de la commande de création de partie (contrat quizup-game) à partir du modèle local
 * de la salle : mapping local → {@code game.GameQuestion}, résolution des joueurs (bot compris) et
 * de leurs progressions. La saga salle orchestre, elle ne mappe pas ; le domaine matchmaking ne
 * dépend jamais du domaine game.
 */
@Service
public class GamePreparationService {

    public GameCommand.CreateGameCommand createGameCommand(String gameId,
                                                           String topicId,
                                                           RoomPlayer initiator,
                                                           RoomPlayer participant,
                                                           boolean vsBot,
                                                           String botDifficulty,
                                                           List<DrawnQuestion> questions) {
        BotDifficulty difficulty = vsBot ? parseBotDifficulty(botDifficulty) : null;
        return new GameCommand.CreateGameCommand(
                gameId,
                topicId,
                initiator.playerId(),
                initiator.playerName(),
                vsBot ? QuizUpConstants.SYSTEM_USER_ID : participant.playerId(),
                vsBot ? QuizUpConstants.SYSTEM_USER_NAME : participant.playerName(),
                vsBot ? GamePlayerType.BOT : GamePlayerType.HUMAN,
                difficulty,
                new PlayerProgressSnapshot(initiator.level(), initiator.xpTotal(), initiator.rating()),
                vsBot
                        ? PlayerProgressSnapshot.forBot(difficulty)
                        : new PlayerProgressSnapshot(participant.level(), participant.xpTotal(), participant.rating()),
                toGameQuestions(questions));
    }

    // ── Mapping du modèle local vers le domaine game (un *-domain ne dépend jamais d'un autre service) ──

    private static List<GameQuestion> toGameQuestions(List<DrawnQuestion> questions) {
        return questions.stream().map(GamePreparationService::toGameQuestion).toList();
    }

    private static GameQuestion toGameQuestion(DrawnQuestion question) {
        Map<Language, GameQuestionContent> translations = new EnumMap<>(Language.class);
        question.translations().forEach((language, content) ->
                translations.put(language, toGameQuestionContent(content)));
        return new GameQuestion(
                question.questionId(),
                translations,
                question.imageUrl(),
                question.difficulty(),
                toGameQuestionChoice(question.correctAnswer()));
    }

    private static GameQuestionContent toGameQuestionContent(DrawnQuestionContent content) {
        Map<GameQuestionChoice, String> answers = new EnumMap<>(GameQuestionChoice.class);
        content.answers().forEach((choice, text) -> {
            GameQuestionChoice mapped = toGameQuestionChoice(choice);
            if (mapped != null) {
                answers.put(mapped, text);
            }
        });
        return new GameQuestionContent(content.text(), answers);
    }

    private static GameQuestionChoice toGameQuestionChoice(DrawnQuestionChoice choice) {
        return choice == null ? null : GameQuestionChoice.valueOf(choice.name());
    }

    private static BotDifficulty parseBotDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return BotDifficulty.NORMAL;
        }
        try {
            return BotDifficulty.valueOf(difficulty);
        } catch (IllegalArgumentException exception) {
            return BotDifficulty.NORMAL;
        }
    }
}
