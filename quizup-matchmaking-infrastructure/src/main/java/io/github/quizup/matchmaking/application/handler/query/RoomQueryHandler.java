package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.RoomExceptions;
import io.github.quizup.matchmaking.domain.model.Room;
import io.github.quizup.matchmaking.domain.port.out.RoomEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.RoomRepositoryPort;
import io.github.quizup.matchmaking.domain.query.RoomQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomQueryHandler {

    private final RoomRepositoryPort roomRepositoryPort;
    private final RoomEventStorePort roomEventStorePort;

    public RoomQueryHandler(RoomRepositoryPort roomRepositoryPort,
                            RoomEventStorePort roomEventStorePort) {
        this.roomRepositoryPort = roomRepositoryPort;
        this.roomEventStorePort = roomEventStorePort;
    }

    @QueryHandler
    public Room handle(RoomQuery.GetRoomById query) {
        return roomRepositoryPort.findById(query.roomId())
                .orElseThrow(() -> new RoomExceptions.RoomNotFoundProblem(query.roomId()));
    }

    @QueryHandler
    public List<Room> handle(RoomQuery.GetMyOpenRooms query) {
        return roomRepositoryPort.findCreatedByPlayerId(query.playerId());
    }

    @QueryHandler
    public List<EventEnvelope> handle(RoomQuery.GetRoomEventsQuery query) {
        return roomEventStorePort.findEventEnvelopesByRoomId(query.roomId());
    }
}
