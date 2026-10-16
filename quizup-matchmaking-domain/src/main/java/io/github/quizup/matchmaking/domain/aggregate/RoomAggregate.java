package io.github.quizup.matchmaking.domain.aggregate;

import io.github.quizup.matchmaking.domain.command.RoomCommand;
import io.github.quizup.matchmaking.domain.event.RoomEvent;
import io.github.quizup.matchmaking.domain.exception.RoomExceptions;
import io.github.quizup.matchmaking.domain.model.RoomDeadline;
import io.github.quizup.matchmaking.domain.model.RoomStatus;
import io.github.quizup.matchmaking.domain.model.RoomWizzRules;
import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * RoomAggregate — salle temps réel : deux humains (salle partagée ou appariement) ou un humain
 * contre le bot.
 * <p>
 * Le créateur est l'initiateur ; le second humain devient participant lors de son
 * <b>apparition</b> dans la salle ({@code JoinRoomCommand}, émis par le client). Seule exception
 * au client-driven : le bot, joint automatiquement par la saga quand la salle est {@code vsBot}.
 * Aucune saga ne simule une action utilisateur, et aucun port externe n'est interrogé dans les
 * handlers. Le statut suit l'avancement de la préparation ({@link RoomStatus}) ; une préparation
 * engagée ne peut plus être quittée, mais l'initiateur peut toujours annuler. Un état terminal est
 * purgé par la saga après rétention, puis {@code markDeleted}.
 */
@Aggregate
public class RoomAggregate {

    private static final Logger logger = LoggerFactory.getLogger(RoomAggregate.class);

    @AggregateIdentifier
    private String roomId;
    private String initiatorId;
    private String opponentId;
    private String participantId;
    private RoomStatus status;
    private boolean initiatorPresent;
    private boolean participantPresent;
    /** Dernière relance (« wizz ») de chaque joueur : cadence anti-spam, jamais bloquante. */
    private Instant initiatorWizzedAt;
    private Instant participantWizzedAt;

    protected RoomAggregate() {
    }

    /**
     * Ouvre une salle. Si {@code opponentId} est renseigné, c'est une salle nominative : seul cet
     * invité pourra apparaître comme second participant. {@code vsBot} marque le second joueur
     * comme bot : sa présence est appliquée <b>dans la même unité de travail</b> que la création —
     * aucune commande d'apparition supplémentaire par salle (le round-trip bloquant de la saga
     * créait plusieurs minutes de lag sous charge, test 1000 parties).
     */
    @CommandHandler
    public RoomAggregate(RoomCommand.CreateRoomCommand command) {
        requirePlayerId(command.initiatorId(), command.roomId());
        requireTopicId(command.topicId(), command.roomId());

        logger.info("Creating room: roomId={}, topicId={}, initiatorId={}, opponentId={}, vsBot={}",
                command.roomId(), command.topicId(), command.initiatorId(), command.opponentId(), command.vsBot());
        Instant now = Instant.now();
        apply(new RoomEvent.RoomCreatedEvent(
                command.roomId(),
                command.topicId(),
                command.initiatorId(),
                command.opponentId(),
                command.vsBot(),
                command.botDifficulty(),
                now.plus(RoomDeadline.ROOM_EXPIRY_DURATION),
                now));
        if (command.vsBot()) {
            apply(new RoomEvent.RoomEnteredEvent(command.roomId(), QuizUpConstants.SYSTEM_USER_ID, null, now));
        }
    }

    /**
     * Apparition d'un joueur dans la salle : présence temps réel et, pour le second humain,
     * enregistrement implicite comme participant. Idempotent ; quand les deux sont présents, la
     * salle passe en préparation ({@code FETCHING_PLAYERS}).
     */
    @CommandHandler
    public void handle(RoomCommand.JoinRoomCommand command) {
        requirePlayerId(command.playerId(), roomId);
        if (isClosed()) {
            return;
        }

        boolean known = command.playerId().equals(initiatorId) || command.playerId().equals(participantId);
        boolean alreadyPresent = command.playerId().equals(initiatorId) ? initiatorPresent : participantPresent;
        if (known && alreadyPresent) {
            return;
        }
        if (!known && StringUtils.isNotBlank(participantId)) {
            throw new RoomExceptions.RoomAlreadyFullProblem(roomId);
        }
        if (!known && StringUtils.isNotBlank(opponentId) && !command.playerId().equals(opponentId)) {
            throw new RoomExceptions.RoomNotInvitedProblem(roomId, command.playerId());
        }

        Instant now = Instant.now();
        logger.info("Player entering room: roomId={}, playerId={}", roomId, command.playerId());
        apply(new RoomEvent.RoomEnteredEvent(roomId, command.playerId(), command.player(), now));
        if (bothPresent()) {
            // La préparation démarre immédiatement ; la partie sera créée dès les questions
            // tirées (plus de compte à rebours de lancement).
            apply(new RoomEvent.RoomAllPlayersPresentEvent(roomId, now));
        }
    }

    /**
     * Sortie <b>non destructive</b> avant l'engagement : le joueur quitte la salle mais le
     * participant reste enregistré (retour possible). Refusée dès que la préparation est lancée
     * (les deux présents) — le duel est engagé. La salle ne se ferme que par
     * {@code CancelRoomCommand} (initiateur) ou expiration.
     */
    @CommandHandler
    public void handle(RoomCommand.LeaveRoomCommand command) {
        if (isClosed()) {
            return;
        }
        requireParticipant(command.playerId());
        if (status != RoomStatus.WAITING_PLAYERS) {
            throw new RoomExceptions.RoomPreparationStartedProblem(roomId);
        }
        boolean alreadyAbsent = command.playerId().equals(initiatorId)
                ? !initiatorPresent
                : !participantPresent;
        if (alreadyAbsent) {
            return;
        }
        apply(new RoomEvent.RoomLeftEvent(roomId, command.playerId(), Instant.now()));
    }

    /**
     * Relance (« wizz ») : le joueur <b>présent</b> demande à l'autre joueur humain — absent — de
     * rejoindre la salle. Pur effet temps réel : la préparation n'est pas déclenchée et la
     * cadence ({@code RoomWizzRules}, 10 s par joueur) borne le harcèlement. Sans cible relançable
     * (salle partagée sans invité, adversaire déjà présent, bot), la commande est refusée ; une
     * préparation engagée ou une salle terminale l'ignore (le second joueur vient d'arriver).
     */
    @CommandHandler
    public void handle(RoomCommand.WizzRoomCommand command) {
        if (isClosed() || status != RoomStatus.WAITING_PLAYERS) {
            // L'adversaire est là (préparation engagée) ou la salle est close : rien à relancer.
            return;
        }
        requireParticipant(command.playerId());
        if (!isPresent(command.playerId())) {
            throw new RoomExceptions.RoomWizzRequesterNotPresentProblem(roomId, command.playerId());
        }
        String targetId = wizzableOpponent(command.playerId());
        if (targetId == null) {
            throw new RoomExceptions.RoomWizzUnavailableProblem(roomId);
        }
        Instant now = Instant.now();
        if (isWithinWizzCooldown(command.playerId(), now)) {
            throw new RoomExceptions.RoomWizzCooldownProblem(roomId, command.playerId());
        }
        logger.info("Player wizzed room: roomId={}, playerId={}, targetId={}",
                roomId, command.playerId(), targetId);
        apply(new RoomEvent.RoomWizzedEvent(roomId, command.playerId(), targetId, now));
    }

    /** L'initiateur annule explicitement la salle, y compris pendant la préparation. */
    @CommandHandler
    public void handle(RoomCommand.CancelRoomCommand command) {
        if (isClosed()) {
            return;
        }
        if (!command.playerId().equals(initiatorId)) {
            throw new RoomExceptions.PlayerNotInRoomProblem(roomId, command.playerId());
        }
        apply(new RoomEvent.RoomCancelledEvent(roomId, initiatorId, "PLAYER_CANCELLED", Instant.now()));
    }

    /** Commande interne (saga) : les profils sont résolus ; avance la préparation (monotone). */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomPlayersLoadedCommand command) {
        if (status != RoomStatus.FETCHING_PLAYERS) {
            return;
        }
        logger.info("Players loaded in room: roomId={}", roomId);
        apply(new RoomEvent.RoomPlayersLoadedEvent(roomId, Instant.now()));
    }

    /** Commande interne (saga) : l'historique des questions jouées est résolu (quizup-game). */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomPlayedQuestionsLoadedCommand command) {
        if (status != RoomStatus.FETCHING_PLAYED_QUESTIONS) {
            return;
        }
        logger.info("Played questions loaded in room: roomId={}", roomId);
        apply(new RoomEvent.RoomPlayedQuestionsLoadedEvent(
                roomId, command.playedQuestionIds(), Instant.now()));
    }

    /** Commande interne (saga) : les questions sont tirées ; la saga lance la création de la partie. */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomQuestionsLoadedCommand command) {
        if (status != RoomStatus.FETCHING_QUESTIONS) {
            return;
        }
        logger.info("Questions loaded in room: roomId={}, création de la partie", roomId);
        apply(new RoomEvent.RoomQuestionsLoadedEvent(roomId, command.questions(), Instant.now()));
    }

    /** Commande interne (saga) : le service game a accusé la création de la partie. */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomGameCreatedCommand command) {
        if (status != RoomStatus.CREATING_GAME) {
            return;
        }
        requireGameId(command.gameId(), roomId);
        logger.info("Game created in room: roomId={}, gameId={}", roomId, command.gameId());
        apply(new RoomEvent.RoomGameCreatedEvent(roomId, command.gameId(), Instant.now()));
    }

    /** Succès : la partie a été créée et accusée ; la salle reste lisible jusqu'à sa purge. */
    @CommandHandler
    public void handle(RoomCommand.CompleteRoomCommand command) {
        requireGameId(command.gameId(), roomId);
        // Clôture uniquement après l'accusé de création (GAME_CREATED) ; idempotent si déjà close.
        if (status != RoomStatus.GAME_CREATED) {
            return;
        }
        // La partie n'est créée qu'une fois les deux joueurs réellement présents en salle.
        if (!bothPresent()) {
            throw new RoomExceptions.ParticipantNotPresentProblem(roomId);
        }
        logger.info("Completing room: roomId={}, gameId={}", roomId, command.gameId());
        apply(new RoomEvent.RoomCompletedEvent(roomId, command.gameId(), Instant.now()));
    }

    /** Échec système : la partie n'a pas pu être préparée (questions insuffisantes, création KO). */
    @CommandHandler
    public void handle(RoomCommand.FailRoomCommand command) {
        if (isClosed()) {
            return;
        }
        logger.warn("Failing room: roomId={}, reason={}", roomId, command.reason());
        apply(new RoomEvent.RoomFailedEvent(roomId, command.reason(), Instant.now()));
    }

    @CommandHandler
    public void handle(RoomCommand.ExpireRoomCommand command) {
        if (isClosed()) {
            return;
        }
        apply(new RoomEvent.RoomExpiredEvent(roomId, Instant.now()));
    }

    @CommandHandler
    public void handle(RoomCommand.PurgeRoomCommand command) {
        logger.info("Purging room: roomId={}", roomId);
        apply(new RoomEvent.RoomPurgedEvent(roomId, Instant.now()));
    }

    // ---------------------------------------------------------------------

    // ── Validateurs (requireXxx : nommés, appelés en tête de handler) ──

    private static void requirePlayerId(String playerId, String roomId) {
        if (StringUtils.isBlank(playerId)) {
            throw new RoomExceptions.MissingPlayerIdentifierProblem(roomId);
        }
    }

    private static void requireTopicId(String topicId, String roomId) {
        if (StringUtils.isBlank(topicId)) {
            throw new RoomExceptions.MissingTopicIdentifierProblem(roomId);
        }
    }

    private static void requireGameId(String gameId, String roomId) {
        if (StringUtils.isBlank(gameId)) {
            throw new RoomExceptions.MissingGameIdentifierProblem(roomId);
        }
    }

    private void requireParticipant(String playerId) {
        if (StringUtils.isBlank(playerId)
                || (!playerId.equals(initiatorId) && !playerId.equals(participantId))) {
            throw new RoomExceptions.PlayerNotInRoomProblem(roomId, playerId);
        }
    }

    /** Présence temps réel d'un participant connu (initiateur ou second enregistré). */
    private boolean isPresent(String playerId) {
        return playerId.equals(initiatorId) ? initiatorPresent : participantPresent;
    }

    /**
     * Adversaire relançable du point de vue du joueur : l'autre humain de la salle, à condition
     * qu'il soit <b>absent</b> (invité nominatif pas encore apparu, participant sorti non
     * destructivement). {@code null} quand il n'y a rien à relancer (salle partagée sans invité,
     * adversaire déjà présent, adversaire système/bot).
     */
    private String wizzableOpponent(String playerId) {
        boolean fromInitiator = playerId.equals(initiatorId);
        String targetId = fromInitiator
                ? (participantId != null ? participantId : opponentId)
                : initiatorId;
        if (StringUtils.isBlank(targetId) || QuizUpConstants.SYSTEM_USER_ID.equals(targetId)) {
            return null;
        }
        boolean targetPresent = fromInitiator ? participantPresent : initiatorPresent;
        return targetPresent ? null : targetId;
    }

    /** Cadence anti-spam : le dernier wizz du joueur n'est pas encore absorbé. */
    private boolean isWithinWizzCooldown(String playerId, Instant now) {
        Instant lastWizzedAt = playerId.equals(initiatorId) ? initiatorWizzedAt : participantWizzedAt;
        return lastWizzedAt != null
                && Duration.between(lastWizzedAt, now).compareTo(RoomWizzRules.WIZZ_COOLDOWN_DURATION) < 0;
    }

    private boolean bothPresent() {
        return initiatorPresent && participantPresent && StringUtils.isNotBlank(participantId);
    }

    private boolean isClosed() {
        return status == RoomStatus.COMPLETED
                || status == RoomStatus.CANCELLED
                || status == RoomStatus.EXPIRED
                || status == RoomStatus.FAILED;
    }

    // ============================ Event Sourcing ============================

    @EventSourcingHandler
    public void on(RoomEvent.RoomCreatedEvent event) {
        this.roomId = event.roomId();
        this.initiatorId = event.initiatorId();
        this.opponentId = event.opponentId();
        this.status = RoomStatus.WAITING_PLAYERS;
    }

    /**
     * Apparition : marque la présence ; le second humain (non initiateur) devient participant au
     * premier passage.
     */
    @EventSourcingHandler
    public void on(RoomEvent.RoomEnteredEvent event) {
        if (event.playerId().equals(initiatorId)) {
            this.initiatorPresent = true;
            return;
        }
        if (StringUtils.isBlank(participantId)) {
            this.participantId = event.playerId();
        }
        if (event.playerId().equals(participantId)) {
            this.participantPresent = true;
        }
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        this.status = RoomStatus.FETCHING_PLAYERS;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomPlayersLoadedEvent event) {
        this.status = RoomStatus.FETCHING_PLAYED_QUESTIONS;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomPlayedQuestionsLoadedEvent event) {
        this.status = RoomStatus.FETCHING_QUESTIONS;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomQuestionsLoadedEvent event) {
        this.status = RoomStatus.CREATING_GAME;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomGameCreatedEvent event) {
        this.status = RoomStatus.GAME_CREATED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomLeftEvent event) {
        // Le participant reste enregistré : il peut revenir (ré-apparition dans la salle).
        if (event.playerId().equals(initiatorId)) {
            this.initiatorPresent = false;
        } else if (event.playerId().equals(participantId)) {
            this.participantPresent = false;
        }
    }

    /** Relance : mémorise l'instant par joueur (cadence anti-spam). */
    @EventSourcingHandler
    public void on(RoomEvent.RoomWizzedEvent event) {
        if (event.playerId().equals(initiatorId)) {
            this.initiatorWizzedAt = event.wizzedAt();
        } else {
            this.participantWizzedAt = event.wizzedAt();
        }
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomCancelledEvent event) {
        this.status = RoomStatus.CANCELLED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomCompletedEvent event) {
        this.status = RoomStatus.COMPLETED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomFailedEvent event) {
        this.status = RoomStatus.FAILED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomExpiredEvent event) {
        this.status = RoomStatus.EXPIRED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomPurgedEvent event) {
        AggregateLifecycle.markDeleted();
    }
}
