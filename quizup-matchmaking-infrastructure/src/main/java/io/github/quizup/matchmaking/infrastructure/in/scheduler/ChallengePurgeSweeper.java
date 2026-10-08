package io.github.quizup.matchmaking.infrastructure.in.scheduler;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.model.ChallengeDeadline;
import io.github.quizup.matchmaking.domain.port.out.ChallengeRepositoryPort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Filet de sécurité de purge des défis : la saga planifie normalement la purge après rétention
 * ({@link ChallengeDeadline#CHALLENGE_PURGE}), mais les défis terminés avant l'introduction de
 * cette saga (ou dont la deadline a été perdue) resteraient sinon à vie dans le read model.
 * <p>
 * Le balayeur cible les défis terminaux plus vieux que la rétention + une marge
 * ({@link ChallengeDeadline#CHALLENGE_SWEEP_GRACE}) afin de laisser la saga faire son travail,
 * puis envoie une commande de purge idempotente (un agrégat déjà supprimé est ignoré).
 */
@Component
public class ChallengePurgeSweeper {

    private static final Logger logger = LoggerFactory.getLogger(ChallengePurgeSweeper.class);

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final CommandGateway commandGateway;

    public ChallengePurgeSweeper(ChallengeRepositoryPort challengeRepositoryPort,
                                 CommandGateway commandGateway) {
        this.challengeRepositoryPort = challengeRepositoryPort;
        this.commandGateway = commandGateway;
    }

    @Scheduled(fixedDelayString = "${app.challenge.purge-sweep-interval-ms:60000}")
    public void sweep() {
        Instant cutoff = Instant.now()
                .minus(ChallengeDeadline.CHALLENGE_RETENTION_DURATION)
                .minus(ChallengeDeadline.CHALLENGE_SWEEP_GRACE);
        challengeRepositoryPort.findTerminalIdsBefore(cutoff).forEach(challengeId -> {
            logger.debug("Purge de rattrapage d'un défi terminal: challengeId={}", challengeId);
            commandGateway.send(new ChallengeCommand.PurgeChallengeCommand(challengeId))
                    .exceptionally(error -> {
                        logger.debug("Purge défi ignorée: challengeId={} ({})",
                                challengeId, error.getMessage());
                        return null;
                    });
        });
    }
}
