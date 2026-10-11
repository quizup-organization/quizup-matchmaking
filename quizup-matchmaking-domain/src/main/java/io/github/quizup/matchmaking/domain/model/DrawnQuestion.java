package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.util.Map;

/**
 * Question tirée pour la préparation d'une partie (miroir local de {@code game.GameQuestion} :
 * un {@code *-domain} ne dépend jamais d'un autre service). Le mapping vers le modèle du
 * domaine game est fait par l'émetteur de la commande (saga salle), cf. {@code command-preparation.md}.
 */
public record DrawnQuestion(
        String questionId,
        Map<Language, DrawnQuestionContent> translations,
        String imageUrl,
        String difficulty,
        DrawnQuestionChoice correctAnswer
) {
}
