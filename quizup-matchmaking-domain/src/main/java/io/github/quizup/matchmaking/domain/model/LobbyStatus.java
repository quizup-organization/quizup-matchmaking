package io.github.quizup.matchmaking.domain.model;

public enum LobbyStatus {
    /** En attente d'un joueur adverse */
    OPEN,
    /** Les deux joueurs sont réunis, partie créée */
    COMPLETED,
    /** Annulé par l'initiateur ou par timeout */
    CANCELLED
}

