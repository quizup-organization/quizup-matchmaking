package io.github.quizup.matchmaking.domain.command;

import io.github.quizup.matchmaking.domain.model.DrawnQuestion;
import io.github.quizup.matchmaking.domain.model.RoomPlayer;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.List;
import java.util.Set;

/**
 * Commandes de la salle : deux humains (salle partagée ou appariement) ou un humain contre le
 * bot. L'identifiant de l'agrégat sert de référence de partage : le lien est {@code /join/{roomId}}.
 */
public interface RoomCommand {

    String roomId();

    /**
     * Ouvre une salle ; le créateur en est l'initiateur. Nominatif si {@code opponentId} est
     * renseigné (défi, appariement) ; {@code vsBot} marque une salle dont le second joueur est le
     * bot (join automatique par la saga). {@code botDifficulty} est le nom de l'enum game
     * (nullable, défaut NORMAL) — porté en {@code String} pour que le domaine matchmaking ne
     * dépende jamais du domaine game.
     */
    record CreateRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            boolean vsBot,
            String botDifficulty
    ) implements RoomCommand {
    }

    /**
     * Le joueur <b>apparaît</b> dans la salle : présence temps réel et, pour le second humain,
     * enregistrement implicite comme participant. Commande unique — le client l'émet quand
     * l'utilisateur est réellement dans la salle ; aucune saga ne le fait à sa place (seule
     * exception : le join automatique du bot sur une salle {@code vsBot}).
     * {@code player} est le snapshot préparé par la façade (profil + progression) : la salle ne
     * fait aucune I/O pour résoudre les joueurs.
     */
    record JoinRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId,
            RoomPlayer player
    ) implements RoomCommand {
    }

    /**
     * Sortie <b>non destructive</b> : le joueur quitte la salle mais reste participant ; il peut
     * revenir jusqu'à l'expiration. Autorisée uniquement tant que les deux joueurs ne sont pas
     * présents ({@code WAITING_PLAYERS}) : une préparation engagée ne peut plus être quittée.
     */
    record LeaveRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /**
     * Relance (« wizz ») : le joueur <b>présent</b> demande à l'autre joueur humain — absent — de
     * rejoindre la salle. Pur effet temps réel (aucune préparation déclenchée) ; cadence par
     * joueur ({@code RoomWizzRules}). Sans cible relançable (salle partagée sans invité, adversaire
     * déjà présent, bot), la commande est refusée ; une préparation engagée l'ignore (le second
     * joueur vient d'arriver).
     */
    record WizzRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /** L'initiateur annule explicitement la salle (possible tant que la salle n'est pas terminale). */
    record CancelRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : les profils des joueurs sont résolus. */
    record MarkRoomPlayersLoadedCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : les questions déjà jouées par les joueurs sont résolues. */
    record MarkRoomPlayedQuestionsLoadedCommand(
            @TargetAggregateIdentifier String roomId,
            Set<String> playedQuestionIds
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : les questions sont tirées, la création de la partie peut démarrer. */
    record MarkRoomQuestionsLoadedCommand(
            @TargetAggregateIdentifier String roomId,
            List<DrawnQuestion> questions
    ) implements RoomCommand {
    }

    /**
     * Commande interne (saga) : le service game a accusé la création de la partie
     * ({@code GameCreatedEvent}). La salle peut être clôturée par {@link CompleteRoomCommand}.
     */
    record MarkRoomGameCreatedCommand(
            @TargetAggregateIdentifier String roomId,
            String gameId
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : la partie a été créée ; la salle reste lisible jusqu'à sa purge. */
    record CompleteRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String gameId
    ) implements RoomCommand {
    }

    /**
     * Commande interne (saga) : la partie n'a pas pu être préparée (questions insuffisantes,
     * création impossible). Le joueur présent est informé par {@code RoomFailedEvent}.
     */
    record FailRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String reason
    ) implements RoomCommand {
    }

    /** Commande interne (saga) : salle jamais lancée, expirée. */
    record ExpireRoomCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }

    /** Commande interne (saga, après rétention) : purge un état terminal. */
    record PurgeRoomCommand(
            @TargetAggregateIdentifier String roomId
    ) implements RoomCommand {
    }
}
