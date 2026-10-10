package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.BaseProblem;
import io.github.quizup.microservice.core.domain.exception.ProblemCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe de base des problèmes métier liés à la salle.
 */
public abstract class RoomProblem extends BaseProblem {

    private final String roomId;

    protected RoomProblem(
            String roomId,
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
                mergeContext(context, roomId)
        );
        this.roomId = roomId;
    }

    protected RoomProblem(
            String roomId,
            String type,
            String title,
            String detail,
            Map<String, Object> context) {
        this(roomId, type, ProblemCategory.BUSINESS_INVALID_COMMAND, title, detail, context);
    }

    protected RoomProblem(
            String roomId,
            String type,
            String title,
            String detail) {
        this(roomId, type, ProblemCategory.BUSINESS_INVALID_COMMAND, title, detail, null);
    }

    private static Map<String, Object> mergeContext(Map<String, Object> context, String roomId) {
        Map<String, Object> merged = new HashMap<>();
        if (context != null) {
            merged.putAll(context);
        }
        merged.put("roomId", roomId);
        return merged;
    }

    public String getRoomId() {
        return roomId;
    }
}
