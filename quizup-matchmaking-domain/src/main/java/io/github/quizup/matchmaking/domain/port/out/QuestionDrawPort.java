package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.DrawnQuestionDifficulty;
import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Port sortant de tirage des questions d'une partie (quizup-theme). Le type retourné est
 * <b>local</b> au domaine matchmaking : un {@code *-domain} ne dépend jamais d'un autre service.
 * Implémenté par un adaptateur {@code application/service} qui interroge le bus.
 * <p>
 * Requête <b>non bloquante</b> : la saga salle ne fait jamais {@code join()} sur le thread de
 * l'event processor — le résultat est renvoyé par commande/événement quand il est disponible.
 */
public interface QuestionDrawPort {

    /**
     * Tire les questions approuvées disponibles dans toutes les langues demandées.
     * {@code target} (difficulté déduite) et {@code excludeQuestionIds} (jamais-jouées) sont des
     * <b>préférences</b> best-effort côté theme.
     */
    CompletableFuture<List<DrawnQuestion>> draw(String topicId, Set<Language> languages,
                                                DrawnQuestionDifficulty target, Set<String> excludeQuestionIds);
}
