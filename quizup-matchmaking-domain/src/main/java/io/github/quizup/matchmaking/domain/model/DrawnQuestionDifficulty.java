package io.github.quizup.matchmaking.domain.model;

/**
 * Difficulté cible d'un tirage de questions — miroir local de {@code theme.QuestionDifficulty}
 * (un {@code *-domain} ne dépend jamais d'un autre service ; le mapping est fait par l'adaptateur).
 */
public enum DrawnQuestionDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT
}
