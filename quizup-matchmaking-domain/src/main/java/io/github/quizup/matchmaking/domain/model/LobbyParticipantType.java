package io.github.quizup.matchmaking.domain.model;

/**
 * Type de participant du point de vue du matchmaking.
 * Volontairement isolé de GamePlayerType (game domain).
 */
public enum LobbyParticipantType {
    HUMAN,
    BOT
}

