package io.github.quizup.matchmaking.domain.query;

/**
 * Queries de la salle.
 */
public interface RoomQuery {

    record GetRoomById(String roomId) implements RoomQuery {
    }

    record GetMyOpenRooms(String playerId) implements RoomQuery {
    }

    record GetRoomEventsQuery(String roomId) implements RoomQuery {
    }
}
