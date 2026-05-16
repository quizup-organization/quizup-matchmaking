package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.common.domain.exception.BaseProblem;
import io.github.quizup.common.domain.exception.ProblemCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe de base pour toutes les exceptions métier liées au domaine Lobby/Matchmaking
 */
public abstract class LobbyProblem extends BaseProblem {

    private final String lobbyId;

    protected LobbyProblem(
            String lobbyId,
            String type,
            ProblemCategory category,
            String title,
            String detail,
            Map<String, Object> context) {
        super(
                type,
                category,
                title,
                detail,
                mergeContext(context, lobbyId)
        );
        this.lobbyId = lobbyId;
    }

    protected LobbyProblem(
            String lobbyId,
            String type,
            String title,
            String detail,
            Map<String, Object> context) {
        this(lobbyId, type, ProblemCategory.BUSINESS_INVALID_COMMAND, title, detail, context);
    }

    protected LobbyProblem(
            String lobbyId,
            String type,
            String title,
            String detail) {
        this(lobbyId, type, ProblemCategory.BUSINESS_INVALID_COMMAND, title, detail, null);
    }

    private static Map<String, Object> mergeContext(Map<String, Object> context, String lobbyId) {
        Map<String, Object> merged = new HashMap<>();
        if (context != null) {
            merged.putAll(context);
        }
        merged.put("lobbyId", lobbyId);
        return merged;
    }

    public String getLobbyId() {
        return lobbyId;
    }
}

