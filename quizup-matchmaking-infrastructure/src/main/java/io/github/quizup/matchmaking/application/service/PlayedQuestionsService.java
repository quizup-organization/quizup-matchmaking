package io.github.quizup.matchmaking.application.service;

import io.github.quizup.game.domain.query.GameQuery;
import io.github.quizup.matchmaking.domain.port.out.PlayedQuestionsPort;
import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Adaptateur sortant du port {@link PlayedQuestionsPort} : interroge quizup-game (questions déjà
 * jouées par les joueurs sur un thème) et mappe la réponse en {@code Set<String>} local — un
 * {@code *-domain} ne dépend jamais d'un autre service.
 * <p>
 * Appelé par la saga salle (fenêtre de préparation) et non par un handler de commande : l'I/O
 * inter-service ne doit jamais bloquer une action utilisateur.
 */
@Service
public class PlayedQuestionsService implements PlayedQuestionsPort {

    private final QueryGateway queryGateway;

    public PlayedQuestionsService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public Set<String> findPlayedQuestionIds(String topicId, Set<String> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Set.of();
        }
        List<String> questionIds = queryGateway.query(
                new GameQuery.GetPlayedQuestionIdsQuery(topicId, playerIds),
                QueryResponseTypes.multipleInstancesOf(String.class)
        ).join();
        return Set.copyOf(questionIds);
    }
}
