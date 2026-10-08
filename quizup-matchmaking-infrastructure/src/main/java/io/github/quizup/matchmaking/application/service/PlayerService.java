package io.github.quizup.matchmaking.application.service;

import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.profile.domain.model.PlayerProgress;
import io.github.quizup.profile.domain.model.Profile;
import io.github.quizup.profile.domain.query.ProgressionQuery;
import io.github.quizup.profile.domain.query.ProfileQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlayerService implements ProfileRepositoryPort {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);

    private final QueryGateway queryGateway;

    public PlayerService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public LobbyPlayer getById(String identifier) {
        Profile profile = queryGateway.query(
                new ProfileQuery.GetProfileQuery(identifier),
                QueryResponseTypes.instanceOf(Profile.class)
        ).join();

        int level = 1;
        int xpTotal = 0;
        try {
            PlayerProgress progress = queryGateway.query(
                    new ProgressionQuery.GetProgressionQuery(identifier),
                    QueryResponseTypes.instanceOf(PlayerProgress.class)
            ).join();
            level = Math.max(1, progress.level());
            xpTotal = Math.max(0, progress.xpTotal());
        } catch (Exception exception) {
            logger.warn("Progression introuvable pour {} : {}", identifier, exception.getMessage());
        }

        return new LobbyPlayer(
                profile.userId(), profile.email(), profile.pseudonym(), profile.language(), level, xpTotal);
    }
}
