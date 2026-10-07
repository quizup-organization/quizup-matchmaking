package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.out.LobbyEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.LobbyStorePort;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LobbyQueryHandler {

    private final LobbyStorePort lobbyStorePort;
    private final LobbyEventStorePort lobbyEventStorePort;

    public LobbyQueryHandler(LobbyStorePort lobbyStorePort,
                             LobbyEventStorePort lobbyEventStorePort) {
        this.lobbyStorePort = lobbyStorePort;
        this.lobbyEventStorePort = lobbyEventStorePort;
    }

    @QueryHandler
    public Lobby handle(LobbyQuery.GetLobbyById query) {
        return lobbyStorePort.findById(query.lobbyId())
                .orElseThrow(() -> new LobbyExceptions.LobbyNotFoundProblem(query.lobbyId()));
    }

    @QueryHandler
    public List<Lobby> handle(LobbyQuery.GetMyOpenLobbies query) {
        return lobbyStorePort.findOpenByPlayerId(query.playerId());
    }

    @QueryHandler
    public List<EventEnvelope> handle(LobbyQuery.GetLobbyEventsQuery query) {
        return lobbyEventStorePort.findEventEnvelopesByLobbyId(query.lobbyId());
    }
}
