package io.github.quizup.matchmaking.application.service;

import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import io.github.quizup.profile.domain.model.PlayerProgress;
import io.github.quizup.profile.domain.model.Profile;
import io.github.quizup.profile.domain.query.ProgressionQuery;
import io.github.quizup.profile.domain.query.ProfileQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Adaptateur sortant inter-module : résout nom, niveau et pays via quizup-profile.
 */
@Service
public class MatchmakingPlayerService implements MatchmakingPlayerPort {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingPlayerService.class);

    private final QueryGateway queryGateway;

    public MatchmakingPlayerService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public PlayerSummary getPlayer(String userId) {
        String displayName = null;
        String country = null;
        int level = 1;

        try {
            Profile profile = queryGateway.query(
                    new ProfileQuery.GetProfileQuery(userId),
                    QueryResponseTypes.instanceOf(Profile.class)
            ).join();
            displayName = profile.displayName();
            country = profile.country();
        } catch (Exception exception) {
            logger.warn("Profil introuvable pour {} : {}", userId, exception.getMessage());
        }

        try {
            PlayerProgress progress = queryGateway.query(
                    new ProgressionQuery.GetProgressionQuery(userId),
                    QueryResponseTypes.instanceOf(PlayerProgress.class)
            ).join();
            level = Math.max(1, progress.level());
        } catch (Exception exception) {
            logger.warn("Progression introuvable pour {} : {}", userId, exception.getMessage());
        }

        return new PlayerSummary(userId, displayName, level, country);
    }
}
