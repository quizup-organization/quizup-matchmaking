package io.github.quizup.matchmaking.application.service;

import io.github.quizup.microservice.core.infrastructure.axon.QueryResponseTypes;
import io.github.quizup.matchmaking.domain.model.PlayerSummary;
import io.github.quizup.matchmaking.domain.port.out.MatchmakingPlayerPort;
import io.github.quizup.profile.domain.model.PlayerPresence;
import io.github.quizup.profile.domain.model.PlayerProgress;
import io.github.quizup.profile.domain.model.PresenceStatus;
import io.github.quizup.profile.domain.model.Profile;
import io.github.quizup.profile.domain.query.PresenceQuery;
import io.github.quizup.profile.domain.query.ProgressionQuery;
import io.github.quizup.profile.domain.query.ProfileQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptateur sortant inter-module : résout nom, niveau et pays via quizup-profile, ainsi que la
 * **présence** temps réel (on n'apparie que des joueurs effectivement en ligne).
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

    @Override
    public Set<String> filterOnline(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        List<PlayerPresence> presences = queryGateway.query(
                new PresenceQuery.GetPresencesByIdsQuery(List.copyOf(userIds)),
                QueryResponseTypes.multipleInstancesOf(PlayerPresence.class)
        ).join();

        return presences.stream()
                .filter(presence -> presence.status() == PresenceStatus.ONLINE)
                .map(PlayerPresence::userId)
                .collect(Collectors.toSet());
    }
}
