package io.github.quizup.matchmaking.domain.model;

import java.util.Map;

/**
 * Contenu localisé d'une question tirée (texte + réponses A-D).
 */
public record DrawnQuestionContent(
        String text,
        Map<DrawnQuestionChoice, String> answers
) {
}
