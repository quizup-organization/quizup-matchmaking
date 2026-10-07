package io.github.quizup.matchmaking.domain.port.out;

import io.github.quizup.matchmaking.domain.event.MatchmakingEvent;
import io.github.quizup.matchmaking.domain.model.Matchmaking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Store chaud (Redis) des tickets d'appariement public : source de vérité de l'état des tickets
 * et file d'attente par sujet. Les transitions critiques (claim d'une paire, bascule bot,
 * annulation) sont atomiques (scripts Lua) — plus de read model asynchrone sur le chemin de
 * décision. Chaque transition terminale écrit son événement dans l'outbox en même temps que
 * l'état : publication Kafka au moins une fois par le relais.
 */
public interface MatchmakingStorePort {

    /** Enfile un ticket (hash + ZSET) et publie {@code MatchmakingStartedEvent}. */
    void enqueue(Matchmaking ticket, MatchmakingEvent.MatchmakingStartedEvent event);

    Optional<Matchmaking> findById(String ticketId);

    /** Candidats du sujet arrivés strictement avant {@code olderThan}, du plus ancien au plus récent. */
    List<Matchmaking> candidates(String topicId, Instant olderThan, int limit);

    /**
     * Réclame atomiquement une paire : les deux tickets doivent être encore {@code SEARCHING},
     * ils passent {@code CLOSED} et sortent de la file. Aucun événement (transition transitoire).
     */
    boolean claimPair(String firstTicketId, String secondTicketId);

    /** Finalise la paire : jeu + adversaires sur les deux tickets, un événement par ticket. */
    void completePair(String firstTicketId,
                      String secondTicketId,
                      MatchmakingEvent.MatchmakingMatchedEvent firstEvent,
                      MatchmakingEvent.MatchmakingMatchedEvent secondEvent);

    /** Réclame atomiquement le ticket pour une bascule bot (toujours {@code SEARCHING}). */
    boolean claimForBot(String ticketId);

    /** Finalise la partie bot (jeu + indicateur) et publie l'événement. */
    void completeBot(String ticketId, MatchmakingEvent.MatchmakingMatchedEvent event);

    /** Échec système (création de partie impossible) : passe {@code FAILED} et publie l'événement. */
    void fail(String ticketId, MatchmakingEvent.MatchmakingFailedEvent event);

    /** Annule un ticket encore {@code SEARCHING} (idempotent sinon) et publie l'événement. */
    boolean cancel(String ticketId, MatchmakingEvent.MatchmakingCancelledEvent event);
}
