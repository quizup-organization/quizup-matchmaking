package io.github.quizup.matchmaking.infrastructure.in.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête de mise en file d'attente pour un sujet.
 */
public record EnqueueMatchmakingRequest(
        @NotBlank(message = "Le sujet est obligatoire")
        String topicId
) {
}
