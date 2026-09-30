package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.model.i18n.Language;

public record LobbyPlayer(String playerId, String playerEmail, String playerName, Language language) {
}
