package io.github.quizup.matchmaking.domain.model;

/**
 * Déduction de la difficulté cible des questions depuis les niveaux des joueurs : moyenne des
 * niveaux **humains** (les niveaux absents — bot — sont ignorés), défaut {@code MEDIUM} si aucun
 * niveau n'est connu.
 */
public interface QuestionLevelRules {

    int MEDIUM_MIN_LEVEL = 10;
    int HARD_MIN_LEVEL = 20;
    int EXPERT_MIN_LEVEL = 40;

    static DrawnQuestionDifficulty targetFor(Integer... levels) {
        double sum = 0;
        int count = 0;
        for (Integer level : levels) {
            if (level != null) {
                sum += level;
                count++;
            }
        }
        if (count == 0) {
            return DrawnQuestionDifficulty.MEDIUM;
        }
        int average = (int) Math.round(sum / count);
        if (average >= EXPERT_MIN_LEVEL) {
            return DrawnQuestionDifficulty.EXPERT;
        }
        if (average >= HARD_MIN_LEVEL) {
            return DrawnQuestionDifficulty.HARD;
        }
        if (average >= MEDIUM_MIN_LEVEL) {
            return DrawnQuestionDifficulty.MEDIUM;
        }
        return DrawnQuestionDifficulty.EASY;
    }
}
