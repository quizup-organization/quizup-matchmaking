package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.Lobby;

import java.util.List;
import java.util.Optional;

/**
 * Store chaud (Redis) des salons : présence, ready-check et cycle de vie. Les transitions
 * (join, entrée, sortie, états terminaux) sont des scripts Lua atomiques qui écrivent l'état
 * et l'événement d'outbox ensemble. Les salons portent un TTL naturel (1 jour ouvert, rétention
 * courte une fois terminé) : plus de projection ni de purge.
 */
public interface LobbyStorePort {

    void create(Lobby lobby, LobbyEvent.LobbyCreatedEvent event);

    Optional<Lobby> findById(String lobbyId);

    /** Salons encore ouverts ({@code CREATED}) où le joueur est initiateur, invité ou participant. */
    List<Lobby> findOpenByPlayerId(String playerId);

    JoinOutcome join(String lobbyId, String playerId, LobbyEvent.LobbyJoinedEvent event);

    EnterOutcome enter(String lobbyId,
                       String playerId,
                       LobbyEvent.LobbyRoomEnteredEvent enteredEvent,
                       LobbyEvent.LobbyAllPlayersPresentEvent allPresentEvent);

    LeaveOutcome leave(String lobbyId, String playerId, LobbyEvent.LobbyLeftEvent event);

    boolean complete(String lobbyId, String gameId, LobbyEvent.LobbyCompletedEvent event);

    boolean fail(String lobbyId, String reason, LobbyEvent.LobbyFailedEvent event);

    boolean miss(String lobbyId, String absentPlayerId, String reason, LobbyEvent.LobbyMissedEvent event);

    boolean cancel(String lobbyId, LobbyEvent.LobbyCancelledEvent event);

    boolean decline(String lobbyId, LobbyEvent.LobbyDeclinedEvent event);

    boolean expire(String lobbyId, LobbyEvent.LobbyExpiredEvent event);

    enum JoinOutcome {
        JOINED, IDEMPOTENT, NOT_INVITED, FULL, CLOSED
    }

    enum EnterOutcome {
        ENTERED, IDEMPOTENT, BOTH_PRESENT, CLOSED, NOT_PARTICIPANT
    }

    enum LeaveOutcome {
        LEFT, IDEMPOTENT, CLOSED, NOT_PARTICIPANT
    }
}
