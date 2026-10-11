package io.github.quizup.matchmaking.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionLevelRulesTest {

    @Test
    void easy_below_ten() {
        assertEquals(DrawnQuestionDifficulty.EASY, QuestionLevelRules.targetFor(1));
        assertEquals(DrawnQuestionDifficulty.EASY, QuestionLevelRules.targetFor(9));
    }

    @Test
    void medium_from_ten_to_nineteen() {
        assertEquals(DrawnQuestionDifficulty.MEDIUM, QuestionLevelRules.targetFor(10));
        assertEquals(DrawnQuestionDifficulty.MEDIUM, QuestionLevelRules.targetFor(19));
    }

    @Test
    void hard_from_twenty_to_thirty_nine() {
        assertEquals(DrawnQuestionDifficulty.HARD, QuestionLevelRules.targetFor(20));
        assertEquals(DrawnQuestionDifficulty.HARD, QuestionLevelRules.targetFor(39));
    }

    @Test
    void expert_from_forty() {
        assertEquals(DrawnQuestionDifficulty.EXPERT, QuestionLevelRules.targetFor(40));
        assertEquals(DrawnQuestionDifficulty.EXPERT, QuestionLevelRules.targetFor(80));
    }

    @Test
    void averages_both_human_levels() {
        assertEquals(DrawnQuestionDifficulty.HARD, QuestionLevelRules.targetFor(30, 10));
        assertEquals(DrawnQuestionDifficulty.EXPERT, QuestionLevelRules.targetFor(41, 42));
    }

    @Test
    void ignores_missing_levels_and_defaults_to_medium() {
        assertEquals(DrawnQuestionDifficulty.EXPERT, QuestionLevelRules.targetFor(50, null));
        assertEquals(DrawnQuestionDifficulty.MEDIUM, QuestionLevelRules.targetFor((Integer) null));
        assertEquals(DrawnQuestionDifficulty.MEDIUM, QuestionLevelRules.targetFor());
    }
}
