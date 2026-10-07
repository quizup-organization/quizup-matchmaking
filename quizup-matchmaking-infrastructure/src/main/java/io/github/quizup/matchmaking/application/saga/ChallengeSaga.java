package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.model.ChallengeDeadline;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Orchestration du défi nominatif (intention asynchrone) : expiration après 1 h sans réponse.
 * <p>
 * L'acceptation crée la <b>salle temps réel</b> : cette responsabilité sera portée par la saga
 * de la salle (écoutant {@code ChallengeAcceptedEvent}) afin de garder chaque cycle de vie isolé.
 */
@Saga
@ProcessingGroup("challenge-saga")
public class ChallengeSaga {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeSaga.class);

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Getter
    @Setter
    private String challengeId;

    @Getter
    @Setter
    private String expiryDeadlineId;

    @StartSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeCreatedEvent event) {
        this.challengeId = event.challengeId();
        this.expiryDeadlineId = deadlineManager.schedule(
                ChallengeDeadline.CHALLENGE_EXPIRY_DURATION,
                ChallengeDeadline.CHALLENGE_EXPIRY);
        logger.info("Défi créé, expiration dans {}h: challengeId={}",
                ChallengeDeadline.CHALLENGE_EXPIRY_DURATION.toHours(), challengeId);
    }

    @DeadlineHandler(deadlineName = ChallengeDeadline.CHALLENGE_EXPIRY)
    public void onExpiry() {
        logger.info("Expiration du défi: challengeId={}", challengeId);
        commandGateway.send(new ChallengeCommand.ExpireChallengeCommand(challengeId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeAcceptedEvent event) {
        cancelExpiry();
        logger.info("Défi accepté: challengeId={}", challengeId);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeDeclinedEvent event) {
        cancelExpiry();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeCancelledEvent event) {
        cancelExpiry();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeExpiredEvent event) {
        cancelExpiry();
    }

    private void cancelExpiry() {
        if (expiryDeadlineId != null) {
            deadlineManager.cancelSchedule(ChallengeDeadline.CHALLENGE_EXPIRY, expiryDeadlineId);
            expiryDeadlineId = null;
        }
    }
}
