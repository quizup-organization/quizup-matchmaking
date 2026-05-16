package io.github.quizup.matchmaking.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;

public record OpenLobbyRequest(@NotBlank String topicId) {}

