package io.github.quizup.matchmaking.application.saga;

import io.github.quizup.matchmaking.domain.command.ChallengeCommand;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.ChallengeEvent;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Crée la <b>salle temps réel</b> à l'acceptation d'un défi nominatif : le défi (intention
 * asynchrone) reste la source de la réponse, la salle porte la présence et le lancement.
 * L'identifiant de salle est déterministe (rejouabilité) et relié au défi.
 */
@Saga
@ProcessingGroup("challenge-room-saga")
public class RoomSaga {

    private static final Logger logger = LoggerFactory.getLogger(RoomSaga.class);

    @Autowired
    private transient CommandGateway commandGateway;

    @Getter
    @Setter
    private String challengeId;

    @Getter
    @Setter
    private String roomId;

    @StartSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeAcceptedEvent event) {
        this.challengeId = event.challengeId();
        this.roomId = deterministicRoomId(event.challengeId());
        logger.info("Défi accepté → création de la salle: challengeId={}, roomId={}",
                challengeId, roomId);
        commandGateway.send(new LobbyCommand.CreateLobbyCommand(
                roomId, event.topicId(), event.challengerId(), event.opponentId()));
        // L'invité est connu : on le marque comme ayant rejoint la salle (le lanceur entrera
        // via EnterLobbyRoom quand son client ouvrira la salle).
        commandGateway.send(new LobbyCommand.JoinLobbyCommand(roomId, event.opponentId()));
        commandGateway.send(new ChallengeCommand.LinkChallengeRoomCommand(event.challengeId(), roomId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "challengeId")
    public void on(ChallengeEvent.ChallengeRoomCreatedEvent event) {
        // Salle reliée au défi : plus rien à orchestrer ici (la salle a sa propre saga).
    }

    private static String deterministicRoomId(String challengeId) {
        return UUID.nameUUIDFromBytes(("room:" + challengeId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
