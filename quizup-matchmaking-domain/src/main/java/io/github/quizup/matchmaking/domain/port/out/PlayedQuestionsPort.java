package io.github.quizup.matchmaking.domain.port.out;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Port sortant de l'historique des questions jouées (quizup-game). Le type retourné est
 * <b>local</b> au domaine matchmaking : un {@code *-domain} ne dépend jamais d'un autre service.
 * Implémenté par un adaptateur {@code application/service} qui interroge le bus.
 * <p>
 * Requête <b>non bloquante</b> : la saga salle ne fait jamais {@code join()} sur le thread de
 * l'event processor — le résultat est renvoyé par commande/événement quand il est disponible.
 */
public interface PlayedQuestionsPort {

    /** Questions déjà jouées par les joueurs sur un thème (rounds réellement affichés). */
    CompletableFuture<Set<String>> findPlayedQuestionIds(String topicId, Set<String> playerIds);
}
