package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.model.ChallengeDeadline;
import io.github.quizup.matchmaking.domain.model.ChallengeRoomId;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

/**
 * Orchestration du défi nominatif : expiration à l'échéance portée par l'événement de création
 * (1 h pour un défi, 10 s pour une revanche), purge après rétention (2 min) et, à l'acceptation,
 * <b>création de la salle</b> (identifiant déterministe {@link ChallengeRoomId}). La saga s'associe
 * ensuite à cette salle pour **acter que le défi a abouti** dès qu'elle produit une partie
 * ({@code ChallengeEvent.ChallengeStartedEvent}). La saga ne joint personne et ne touche jamais à la
 * présence : le client seul fait apparaître le joueur dans la salle ({@code JoinRoomCommand}).
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

    @Getter
    @Setter
    private String purgeDeadlineId;

    @Getter
    @Setter
    private boolean roomRequested;

    @StartSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeCreatedEvent event) {
        this.challengeId = event.challengeId();
        // L'agrégat porte l'échéance absolue (1 h, ou 10 s pour une revanche) : la saga planifie
        // exactement cet instant, jamais une durée recalculée depuis son propre horloge — sinon
        // la notification et l'agrégat expireraient à des moments différents.
        Duration ttl = Duration.between(event.createdAt(), event.expiresAt());
        this.expiryDeadlineId = deadlineManager.schedule(
                event.expiresAt(), ChallengeDeadline.CHALLENGE_EXPIRY);
        logger.info("Défi créé, TTL {} ms: challengeId={}, rematch={}",
                ttl.toMillis(), challengeId, event.gameId() != null);
    }

    @DeadlineHandler(deadlineName = ChallengeDeadline.CHALLENGE_EXPIRY)
    public void onExpiry() {
        logger.info("Expiration du défi: challengeId={}", challengeId);
        commandGateway.send(new ChallengeCommand.ExpireChallengeCommand(challengeId));
    }

    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeAcceptedEvent event) {
        cancelExpiry();
        schedulePurge();
        if (!roomRequested) {
            roomRequested = true;
            String roomId = ChallengeRoomId.of(event.challengeId());
            logger.info("Défi accepté → création de la salle: challengeId={}, roomId={}", challengeId, roomId);
            // La salle est déterministe : on s'y associe pour savoir **quand le défi aboutit**
            // (la salle produit la partie — cf. `RoomEvent.RoomGameCreatedEvent`).
            SagaLifecycle.associateWith("roomId", roomId);
            commandGateway.send(new RoomCommand.CreateRoomCommand(
                    roomId, event.topicId(), event.challengerId(), event.opponentId(), false, null));
        }
    }

    /**
     * La salle du défi a produit une partie : le défi « a abouti » (l'invité s'est présenté).
     * La saga reste vivante (rétention puis purge).
     */
    @SagaEventHandler(associationProperty = "roomId")
    public void on(RoomEvent.RoomGameCreatedEvent event) {
        logger.info("Défi abouti (partie créée): challengeId={}, gameId={}", challengeId, event.gameId());
        commandGateway.send(new ChallengeCommand.StartChallengeCommand(challengeId, event.gameId()));
    }

    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeDeclinedEvent event) {
        cancelExpiry();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeCancelledEvent event) {
        cancelExpiry();
        schedulePurge();
    }

    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeExpiredEvent event) {
        cancelExpiry();
        schedulePurge();
    }

    @DeadlineHandler(deadlineName = ChallengeDeadline.CHALLENGE_PURGE)
    public void onPurge() {
        commandGateway.send(new ChallengeCommand.PurgeChallengeCommand(challengeId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengePurgedEvent event) {
        cancelAllDeadlines();
        logger.info("Saga défi terminée (purgé): challengeId={}", challengeId);
    }

    private void schedulePurge() {
        if (purgeDeadlineId == null) {
            purgeDeadlineId = deadlineManager.schedule(
                    ChallengeDeadline.CHALLENGE_RETENTION_DURATION,
                    ChallengeDeadline.CHALLENGE_PURGE);
        }
    }

    private void cancelAllDeadlines() {
        cancelExpiry();
        if (purgeDeadlineId != null) {
            deadlineManager.cancelSchedule(ChallengeDeadline.CHALLENGE_PURGE, purgeDeadlineId);
            purgeDeadlineId = null;
        }
    }

    private void cancelExpiry() {
        if (expiryDeadlineId != null) {
            deadlineManager.cancelSchedule(ChallengeDeadline.CHALLENGE_EXPIRY, expiryDeadlineId);
            expiryDeadlineId = null;
        }
    }
}
