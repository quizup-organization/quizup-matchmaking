package io.github.quizup.matchmaking.infrastructure.in.api;

import io.github.quizup.matchmaking.domain.port.in.GetLobbyUseCase;
import io.github.quizup.matchmaking.domain.port.in.MatchmakingUseCase;
import io.github.quizup.matchmaking.infrastructure.in.api.mapper.MatchmakingResponseMapper;
import io.github.quizup.matchmaking.infrastructure.in.api.request.EnqueueMatchmakingRequest;
import io.github.quizup.matchmaking.infrastructure.in.api.response.MatchmakingTicketResponse;
import io.github.quizup.microservice.core.infrastructure.in.api.ResponseEntityBuilder;
import io.github.quizup.microservice.core.infrastructure.in.api.response.IdResponse;
import io.github.quizup.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * File d'attente de matchmaking — façade exposée au client.
 *
 * <p>Le ticket n'est qu'une vue de {@code Lobby} : l'appariement (sujet, niveau
 * ±5, préférence pays) vit dans {@code MatchmakingService} ; le fallback bot et
 * la création de la partie sont portés par {@code LobbySaga}.
 *
 * <p>Les notifications live sont publiées sur {@code /topic/lobbies/{ticketId}}.
 */
@RestController
@RequestMapping(MatchmakingController.ENDPOINT)
public class MatchmakingController {

    public static final String ENDPOINT = "/api/matchmaking/queue";

    private final MatchmakingUseCase matchmakingUseCase;
    private final GetLobbyUseCase getLobbyUseCase;

    public MatchmakingController(MatchmakingUseCase matchmakingUseCase,
                                 GetLobbyUseCase getLobbyUseCase) {
        this.matchmakingUseCase = matchmakingUseCase;
        this.getLobbyUseCase = getLobbyUseCase;
    }

    /**
     * Met le joueur en file pour un sujet et renvoie son ticket.
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<IdResponse>> enqueue(
            @RequestBody @Valid EnqueueMatchmakingRequest request
    ) {
        String playerId = SecurityHelper.getUserId();
        return matchmakingUseCase.enqueue(playerId, request.topicId())
                .thenApply(ticketId -> ResponseEntityBuilder.creation(ENDPOINT, ticketId));
    }

    /**
     * État du ticket.
     */
    @GetMapping("/{ticketId}")
    public CompletableFuture<ResponseEntity<MatchmakingTicketResponse>> get(@PathVariable String ticketId) {
        return getLobbyUseCase.getById(ticketId)
                .thenApply(MatchmakingResponseMapper::toTicket)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Annule la recherche (initiateur seulement) — annule le lobby associé au ticket
     * ({@code LobbyCancelledEvent}), donc {@code POST /{id}/cancel} et non un {@code DELETE}.
     */
    @PostMapping("/{ticketId}/cancel")
    public CompletableFuture<ResponseEntity<IdResponse>> cancel(@PathVariable String ticketId) {
        String playerId = SecurityHelper.getUserId();
        return matchmakingUseCase.cancel(playerId, ticketId)
                .thenApply(ResponseEntityBuilder::ok);
    }
}
