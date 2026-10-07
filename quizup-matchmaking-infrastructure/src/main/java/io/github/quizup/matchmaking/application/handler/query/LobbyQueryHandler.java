package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.out.LobbyEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import io.github.quizup.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LobbyQueryHandler {

    private final LobbyRepositoryPort lobbyRepositoryPort;
    private final LobbyEventStorePort lobbyEventStorePort;

    public LobbyQueryHandler(LobbyRepositoryPort lobbyRepositoryPort,
                             LobbyEventStorePort lobbyEventStorePort) {
        this.lobbyRepositoryPort = lobbyRepositoryPort;
        this.lobbyEventStorePort = lobbyEventStorePort;
    }

    @QueryHandler
    public Lobby handle(LobbyQuery.GetLobbyById query) {
        return lobbyRepositoryPort.findById(query.lobbyId())
                .orElseThrow(() -> new LobbyExceptions.LobbyNotFoundProblem(query.lobbyId()));
    }

    @QueryHandler
    public List<Lobby> handle(LobbyQuery.GetMyOpenLobbies query) {
        return lobbyRepositoryPort.findCreatedByPlayerId(query.playerId());
    }

    @QueryHandler
    public List<EventEnvelope> handle(LobbyQuery.GetLobbyEventsQuery query) {
        return lobbyEventStorePort.findEventEnvelopesByLobbyId(query.lobbyId());
    }
}
