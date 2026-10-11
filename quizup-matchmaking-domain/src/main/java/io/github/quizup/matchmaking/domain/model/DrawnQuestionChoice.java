package io.github.quizup.matchmaking.domain.model;

/**
 * Choix de réponse d'une question tirée (miroir local de {@code game.GameQuestionChoice} :
 * un {@code *-domain} ne dépend jamais d'un autre service).
 */
public enum DrawnQuestionChoice {
    A,
    B,
    C,
    D
}
