package io.github.quizup.matchmaking.domain.port.out;

import java.util.Set;

/**
 * Port sortant de l'historique des questions jouées (quizup-game). Le type retourné est
 * <b>local</b> au domaine matchmaking : un {@code *-domain} ne dépend jamais d'un autre service.
 * Implémenté par un adaptateur {@code application/service} qui interroge le bus.
 */
public interface PlayedQuestionsPort {

    /** Questions déjà jouées par les joueurs sur un thème (rounds réellement affichés). */
    Set<String> findPlayedQuestionIds(String topicId, Set<String> playerIds);
}
