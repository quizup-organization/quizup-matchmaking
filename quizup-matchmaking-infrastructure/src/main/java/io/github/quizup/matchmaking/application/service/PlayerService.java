package io.github.quizup.matchmaking.application.service;

import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.profile.domain.model.Profile;
import io.github.quizup.profile.domain.query.ProfileQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

@Service
public class PlayerService implements ProfileRepositoryPort {

    private final QueryGateway queryGateway;

    public PlayerService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public LobbyPlayer getById(String identifier) {
        Profile profile =  queryGateway.query(
                new ProfileQuery.GetProfileQuery(identifier),
                QueryResponseTypes.instanceOf(Profile.class)
        ).join();
        return new LobbyPlayer(profile.userId(), profile.email(), profile.displayName());
    }
}
