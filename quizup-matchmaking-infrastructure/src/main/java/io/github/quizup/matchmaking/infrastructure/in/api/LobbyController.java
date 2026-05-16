package io.github.quizup.matchmaking.infrastructure.in.api;

import io.github.quizup.common.domain.model.search.SearchCriteria;
import io.github.quizup.common.infrastructure.in.api.ResponseEntityBuilder;
import io.github.quizup.common.infrastructure.in.api.request.SearchRequest;
import io.github.quizup.common.infrastructure.in.api.response.IdResponse;
import io.github.quizup.common.infrastructure.in.api.response.PageResponse;
import io.github.quizup.common.infrastructure.mapper.SearchRequestMapper;
import io.github.quizup.matchmaking.domain.command.LobbyCommand;
import io.github.quizup.matchmaking.domain.model.LobbyParticipantType;
import io.github.quizup.matchmaking.domain.port.in.CancelLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyEventsUseCase;
import io.github.quizup.matchmaking.domain.port.in.GetLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.JoinLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.OpenLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.SearchLobbyUseCase;
import io.github.quizup.matchmaking.infrastructure.in.api.mapper.LobbyResponseMapper;
import io.github.quizup.matchmaking.infrastructure.in.api.request.OpenLobbyRequest;
import io.github.quizup.matchmaking.infrastructure.in.api.response.LobbyResponse;
import io.github.quizup.matchmaking.infrastructure.out.messaging.mapper.LobbyEventNotificationMapper;
import io.github.quizup.matchmaking.infrastructure.out.messaging.response.LobbyNotification;
import io.github.quizup.microservice.infrastructure.security.SecurityHelper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST API du matchmaking.
 * <p>
 * POST   /api/lobbies/search        → Recherche paginée de lobbies
 * POST   /api/lobbies               → Crée un nouveau lobby
 * GET    /api/lobbies/{id}          → Détail d'un lobby
 * POST   /api/lobbies/{id}/join     → Rejoint un lobby existant
 * DELETE /api/lobbies/{id}          → Annule le lobby (initiateur seulement)
 * <p>
 * Flow client recommandé :
 * 1. POST /search avec les filtres du client
 * → liste non vide → choisir un lobby → POST /{id}/join
 * → liste vide     → POST / pour créer
 */
@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private static final String ENDPOINT = "/api/lobbies";

    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);

    private final OpenLobbyUseCase openLobbyUseCase;
    private final JoinLobbyUseCase joinLobbyUseCase;
    private final CancelLobbyUseCase cancelLobbyUseCase;
    private final GetLobbyUseCase getLobbyUseCase;
    private final SearchLobbyUseCase searchLobbyUseCase;
    private final GetLobbyEventsUseCase getLobbyEventsUseCase;

    public LobbyController(OpenLobbyUseCase openLobbyUseCase,
                           JoinLobbyUseCase joinLobbyUseCase,
                           CancelLobbyUseCase cancelLobbyUseCase,
                           GetLobbyUseCase getLobbyUseCase,
                           SearchLobbyUseCase searchLobbyUseCase,
                           GetLobbyEventsUseCase getLobbyEventsUseCase) {
        this.openLobbyUseCase = openLobbyUseCase;
        this.joinLobbyUseCase = joinLobbyUseCase;
        this.cancelLobbyUseCase = cancelLobbyUseCase;
        this.getLobbyUseCase = getLobbyUseCase;
        this.searchLobbyUseCase = searchLobbyUseCase;
        this.getLobbyEventsUseCase = getLobbyEventsUseCase;
    }

    /**
     * Liste les lobbies ouverts pour un topic.
     * Retourne toujours 200 (liste vide si aucun lobby disponible).
     */
    @PostMapping("/search")
    public CompletableFuture<ResponseEntity<PageResponse<LobbyResponse>>> search(@RequestBody SearchRequest searchRequest) {
        SearchCriteria searchCriteria = SearchRequestMapper.toSearchCriteria(searchRequest);
        return searchLobbyUseCase
                .search(searchCriteria.filters(), searchCriteria.sorts(), searchCriteria.page())
                .thenApply(LobbyResponseMapper::toResponse)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Crée un nouveau lobby pour le joueur courant.
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> create(@RequestBody @Valid OpenLobbyRequest request) {
        String playerId = SecurityHelper.getUserId();
        String lobbyId = UUID.randomUUID().toString();

        logger.info("Création lobby: lobbyId={}, initiator={}, topicId={}", lobbyId, playerId, request.topicId());

        return openLobbyUseCase
                .open(new LobbyCommand.OpenLobbyCommand(lobbyId, playerId, request.topicId()))
                .thenApply(_ -> ResponseEntityBuilder.creation(ENDPOINT, lobbyId));
    }

    /**
     * Récupère un lobby par son identifiant.
     */
    @GetMapping("/{lobbyId}")
    public CompletableFuture<ResponseEntity<LobbyResponse>> get(@PathVariable String lobbyId) {
        logger.info("Récupération lobby: lobbyId={}", lobbyId);

        return getLobbyUseCase.getById(lobbyId)
                .thenApply(LobbyResponseMapper::toResponse)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{lobbyId}/notifications")
    public CompletableFuture<ResponseEntity<Collection<LobbyNotification>>> notifications(@PathVariable String lobbyId) {
        logger.info("Récupération notifications lobby: lobbyId={}", lobbyId);

        return getLobbyEventsUseCase.getEvents(lobbyId)
                .thenApply(events -> events.stream()
                        .map(LobbyEventNotificationMapper::toNotification)
                        .flatMap(java.util.Optional::stream)
                        .toList())
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Rejoint un lobby existant.
     */
    @PostMapping("/{lobbyId}/join")
    public CompletableFuture<ResponseEntity<IdResponse>> join(@PathVariable String lobbyId) {
        String playerId = SecurityHelper.getUserId();
        logger.info("Rejoindre lobby: lobbyId={}, playerId={}", lobbyId, playerId);

        return joinLobbyUseCase
                .join(lobbyId, playerId, LobbyParticipantType.HUMAN)
                .thenApply(ResponseEntityBuilder::ok);
    }

    /**
     * Annule un lobby (initiateur seulement).
     */
    @DeleteMapping("/{lobbyId}")
    public CompletableFuture<ResponseEntity<IdResponse>> cancel(@PathVariable String lobbyId) {
        String playerId = SecurityHelper.getUserId();
        logger.info("Annulation lobby: lobbyId={}, initiatorId={}", lobbyId, playerId);

        return cancelLobbyUseCase
                .cancel(lobbyId, playerId)
                .thenApply(ResponseEntityBuilder::ok);
    }
}