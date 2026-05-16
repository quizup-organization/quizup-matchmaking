package io.github.quizup.matchmaking.application.handler.query;

import io.github.quizup.common.domain.model.search.PageResult;
import io.github.quizup.matchmaking.domain.exception.LobbyExceptions;
import io.github.quizup.matchmaking.domain.event.LobbyEvent;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.port.out.LobbyEventStorePort;
import io.github.quizup.matchmaking.domain.port.out.LobbyRepositoryPort;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;
import org.axonframework.queryhandling.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LobbyQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(LobbyQueryHandler.class);

    private final LobbyRepositoryPort lobbyRepositoryPort;
    private final LobbyEventStorePort lobbyEventStorePort;

    public LobbyQueryHandler(LobbyRepositoryPort lobbyRepositoryPort,
                             LobbyEventStorePort lobbyEventStorePort) {
        this.lobbyRepositoryPort = lobbyRepositoryPort;
        this.lobbyEventStorePort = lobbyEventStorePort;
    }

    @QueryHandler
    public Optional<Lobby> handle(LobbyQuery.FindFirstOpenLobbyByTopicId query) {
        logger.info("FindFirstOpenLobbyByTopicId: topicId={}", query.topicId());
        return lobbyRepositoryPort.findFirstOpenByTopicId(query.topicId());
    }

    @QueryHandler
    public List<Lobby> handle(LobbyQuery.FindOpenLobbiesByTopicId query) {
        logger.info("FindOpenLobbiesByTopicId: topicId={}", query.topicId());
        return lobbyRepositoryPort.findOpenByTopicId(query.topicId());
    }

    @QueryHandler
    public Optional<Lobby> handle(LobbyQuery.FindLobbyById query) {
        logger.info("FindLobbyById: lobbyId={}", query.lobbyId());
        return lobbyRepositoryPort.findById(query.lobbyId());
    }

    @QueryHandler
    public Lobby handle(LobbyQuery.GetLobbyById query) {
        logger.info("GetLobbyById: lobbyId={}", query.lobbyId());
        return lobbyRepositoryPort.findById(query.lobbyId())
                .orElseThrow(() -> new LobbyExceptions.LobbyNotFoundProblem(query.lobbyId()));
    }

    @QueryHandler
    public List<LobbyEvent> handle(LobbyQuery.GetLobbyEventsQuery query) {
        logger.info("GetLobbyEvents: lobbyId={}", query.lobbyId());
        return lobbyEventStorePort.findAllByLobbyId(query.lobbyId());
    }

    @QueryHandler
    public PageResult<Lobby> handle(LobbyQuery.SearchLobbyQuery query) {
        return lobbyRepositoryPort.findAll(query);
    }
}
