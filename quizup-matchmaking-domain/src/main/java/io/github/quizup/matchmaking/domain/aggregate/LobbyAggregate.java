package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.LobbyPlayer;
import io.github.quizup.matchmaking.domain.model.LobbyStatus;
import io.github.quizup.matchmaking.domain.port.out.ProfileRepositoryPort;
import io.github.quizup.matchmaking.domain.port.out.TopicAvailabilityPort;
import io.github.quizup.microservice.core.domain.model.i18n.Language;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * LobbyAggregate — salon privé : salle d'attente entre deux humains.
 * <p>
 * Le créateur est le premier participant ; le second rejoint via {@code JoinLobbyCommand}.
 * Un état terminal (partie créée, annulé, expiré, échoué) est purgé par la saga après rétention
 * (deadline), puis {@code markDeleted}. Aucune notion de bot ni d'appariement ici.
 */
@Aggregate
public class LobbyAggregate {

    private static final Logger logger = LoggerFactory.getLogger(LobbyAggregate.class);

    @AggregateIdentifier
    private String lobbyId;
    private String topicId;
    private String initiatorId;
    private String opponentId;
    private String participantId;
    private LobbyStatus status;
    private boolean initiatorPresent;
    private boolean participantPresent;

    protected LobbyAggregate() {
    }

    /**
     * Ouvre un salon privé. Si {@code opponentId} est renseigné, c'est un défi nominatif :
     * seul cet invité pourra rejoindre, et le thème doit couvrir les langues des deux joueurs.
     */
    @CommandHandler
    public LobbyAggregate(LobbyCommand.CreateLobbyCommand command,
                          ProfileRepositoryPort profileRepositoryPort,
                          TopicAvailabilityPort topicAvailabilityPort) {
        if (StringUtils.isBlank(command.initiatorId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(command.lobbyId());
        }
        if (StringUtils.isBlank(command.topicId())) {
            throw new LobbyExceptions.MissingTopicIdentifierProblem(command.lobbyId());
        }
        if (StringUtils.isNotBlank(command.opponentId())) {
            if (command.opponentId().equals(command.initiatorId())) {
                throw new LobbyExceptions.CannotChallengeSelfProblem(command.lobbyId(), command.initiatorId());
            }
            requireTopicCoversLanguages(profileRepositoryPort, topicAvailabilityPort,
                    command.initiatorId(), command.opponentId(), command.topicId());
        }
        logger.info("Creating lobby: lobbyId={}, topicId={}, initiatorId={}, opponentId={}",
                command.lobbyId(), command.topicId(), command.initiatorId(), command.opponentId());
        apply(new LobbyEvent.LobbyCreatedEvent(
                command.lobbyId(),
                command.topicId(),
                command.initiatorId(),
                command.opponentId(),
                Instant.now().plus(io.github.quizup.matchmaking.domain.model.LobbyDeadline.LOBBY_EXPIRY_DURATION),
                Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.JoinLobbyCommand command) {
        if (isClosed()) {
            throw new LobbyExceptions.LobbyNotAvailableProblem(lobbyId);
        }
        if (StringUtils.isBlank(command.playerId())) {
            throw new LobbyExceptions.MissingPlayerIdentifierProblem(lobbyId);
        }
        if (command.playerId().equals(initiatorId) || command.playerId().equals(participantId)) {
            // Idempotent : rejoindre à nouveau son propre salon ne fait rien.
            return;
        }
        if (StringUtils.isNotBlank(opponentId) && !command.playerId().equals(opponentId)) {
            throw new LobbyExceptions.LobbyNotInvitedProblem(lobbyId, command.playerId());
        }
        if (StringUtils.isNotBlank(participantId)) {
            throw new LobbyExceptions.LobbyAlreadyFullProblem(lobbyId);
        }
        logger.info("Joining lobby: lobbyId={}, participantId={}", lobbyId, command.playerId());
        apply(new LobbyEvent.LobbyJoinedEvent(lobbyId, command.playerId(), Instant.now()));
    }

    /**
     * Entrée effective dans la salle (présence temps réel). Idempotent ; quand les deux joueurs
     * sont présents, la salle déclenche le compte à rebours de lancement.
     */
    @CommandHandler
    public void handle(LobbyCommand.EnterLobbyRoomCommand command) {
        if (isClosed()) {
            return;
        }
        requireParticipant(command.playerId());
        boolean alreadyPresent = command.playerId().equals(initiatorId)
                ? initiatorPresent
                : participantPresent;
        if (alreadyPresent) {
            return;
        }
        Instant now = Instant.now();
        apply(new LobbyEvent.LobbyRoomEnteredEvent(lobbyId, command.playerId(), now));
        if (bothPresent()) {
            apply(new LobbyEvent.LobbyAllPlayersPresentEvent(
                    lobbyId,
                    now.plus(io.github.quizup.matchmaking.domain.model.LobbyDeadline.LOBBY_READY_CHECK_DURATION),
                    now));
        }
    }

    /** Commande interne : un joueur ne s'est jamais présenté / a disparu, la salle est close. */
    @CommandHandler
    public void handle(LobbyCommand.MissLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        String absentPlayerId = StringUtils.isNotBlank(command.absentPlayerId())
                ? command.absentPlayerId()
                : computedAbsentPlayerId();
        apply(new LobbyEvent.LobbyMissedEvent(
                lobbyId, absentPlayerId, command.reason(), Instant.now()));
    }

    private String computedAbsentPlayerId() {
        if (!initiatorPresent) {
            return initiatorId;
        }
        return participantPresent ? null : participantId;
    }

    /** Refus d'un défi nominatif par l'invité : le salon est clos. */
    @CommandHandler
    public void handle(LobbyCommand.DeclineLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        if (StringUtils.isBlank(opponentId) || !command.playerId().equals(opponentId)) {
            throw new LobbyExceptions.LobbyNotInvitedProblem(lobbyId, command.playerId());
        }
        logger.info("Declining lobby: lobbyId={}, opponentId={}", lobbyId, command.playerId());
        apply(new LobbyEvent.LobbyDeclinedEvent(lobbyId, initiatorId, opponentId, Instant.now()));
    }

    /**
     * Sortie **non destructive** : le joueur quitte la salle mais le salon reste ouvert (il peut
     * y revenir via son lien ou l'inbox). Le salon ne se ferme que par {@code CancelLobbyCommand}
     * (initiateur), annulation/refus, ou expiration — jamais sur passage hors ligne.
     */
    @CommandHandler
    public void handle(LobbyCommand.LeaveLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        requireParticipant(command.playerId());
        boolean alreadyAbsent = command.playerId().equals(initiatorId)
                ? !initiatorPresent
                : !participantPresent;
        if (alreadyAbsent) {
            return;
        }
        apply(new LobbyEvent.LobbyLeftEvent(lobbyId, command.playerId(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.CancelLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        if (!command.playerId().equals(initiatorId)) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(lobbyId, command.playerId());
        }
        apply(new LobbyEvent.LobbyCancelledEvent(lobbyId, initiatorId, "PLAYER_CANCELLED", Instant.now()));
    }

    /** Succès : la partie est créée ; le salon reste lisible jusqu'à sa purge par la saga. */
    @CommandHandler
    public void handle(LobbyCommand.CompleteLobbyCommand command) {
        if (StringUtils.isBlank(command.gameId())) {
            throw new LobbyExceptions.MissingGameIdentifierProblem(lobbyId);
        }
        // La partie n'est créée qu'une fois les deux joueurs réellement présents en salle.
        if (!bothPresent()) {
            throw new LobbyExceptions.ParticipantNotPresentProblem(lobbyId);
        }
        logger.info("Completing lobby: lobbyId={}, gameId={}", lobbyId, command.gameId());
        apply(new LobbyEvent.LobbyCompletedEvent(lobbyId, command.gameId(), Instant.now()));
    }

    /** Échec système : la partie n'a pas pu être créée. */
    @CommandHandler
    public void handle(LobbyCommand.FailLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        logger.warn("Failing lobby: lobbyId={}, reason={}", lobbyId, command.reason());
        apply(new LobbyEvent.LobbyFailedEvent(lobbyId, command.reason(), Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.ExpireLobbyCommand command) {
        if (isClosed()) {
            return;
        }
        apply(new LobbyEvent.LobbyExpiredEvent(lobbyId, Instant.now()));
    }

    @CommandHandler
    public void handle(LobbyCommand.PurgeLobbyCommand command) {
        logger.info("Purging lobby: lobbyId={}", lobbyId);
        apply(new LobbyEvent.LobbyPurgedEvent(lobbyId, Instant.now()));
    }

    // ---------------------------------------------------------------------

    private void requireParticipant(String playerId) {
        if (StringUtils.isBlank(playerId)
                || (!playerId.equals(initiatorId) && !playerId.equals(participantId))) {
            throw new LobbyExceptions.PlayerNotInLobbyProblem(lobbyId, playerId);
        }
    }

    private boolean bothPresent() {
        return initiatorPresent && participantPresent && StringUtils.isNotBlank(participantId);
    }

    /**
     * Un défi nominatif n'est créé que si le thème couvre les langues des deux joueurs
     * (sélection stricte côté game) — échec rapide plutôt qu'à la jointure.
     */
    private static void requireTopicCoversLanguages(ProfileRepositoryPort profileRepositoryPort,
                                                    TopicAvailabilityPort topicAvailabilityPort,
                                                    String initiatorId,
                                                    String opponentId,
                                                    String topicId) {
        Set<Language> languages = new HashSet<>();
        LobbyPlayer initiator = profileRepositoryPort.getById(initiatorId);
        LobbyPlayer opponent = profileRepositoryPort.getById(opponentId);
        if (initiator != null && initiator.language() != null) {
            languages.add(initiator.language());
        }
        if (opponent != null && opponent.language() != null) {
            languages.add(opponent.language());
        }
        if (!languages.isEmpty() && !topicAvailabilityPort.coversAllLanguages(topicId, languages)) {
            throw new LobbyExceptions.TopicNotAvailableInLanguageProblem(topicId, languages);
        }
    }

    private boolean isClosed() {
        return status == LobbyStatus.CLOSED || status == LobbyStatus.FAILED;
    }

    // ============================ Event Sourcing ============================

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCreatedEvent event) {
        this.lobbyId = event.lobbyId();
        this.topicId = event.topicId();
        this.initiatorId = event.initiatorId();
        this.opponentId = event.opponentId();
        this.status = LobbyStatus.CREATED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyJoinedEvent event) {
        this.participantId = event.participantId();
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyRoomEnteredEvent event) {
        if (event.playerId().equals(initiatorId)) {
            this.initiatorPresent = true;
        } else {
            this.participantPresent = true;
        }
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyAllPlayersPresentEvent event) {
        // Événement de lancement : l'état des présences est déjà porté par les entrées.
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyMissedEvent event) {
        this.status = LobbyStatus.CLOSED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyDeclinedEvent event) {
        this.status = LobbyStatus.CLOSED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCompletedEvent event) {
        this.status = LobbyStatus.CLOSED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyLeftEvent event) {
        // Le participant reste enregistré : il peut revenir (ré-entrée dans la salle).
        if (event.playerId().equals(initiatorId)) {
            this.initiatorPresent = false;
        } else if (event.playerId().equals(participantId)) {
            this.participantPresent = false;
        }
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyCancelledEvent event) {
        this.status = LobbyStatus.CLOSED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyFailedEvent event) {
        this.status = LobbyStatus.FAILED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyExpiredEvent event) {
        this.status = LobbyStatus.CLOSED;
    }

    @EventSourcingHandler
    public void on(LobbyEvent.LobbyPurgedEvent event) {
        AggregateLifecycle.markDeleted();
    }
}
