package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.BaseProblem;
import io.github.quizup.microservice.core.domain.exception.ProblemCategory;

import java.util.HashMap;
import java.util.Map;

/** Classe de base des problèmes métier du défi nominatif. */
public abstract class ChallengeProblem extends BaseProblem {

    private final String challengeId;

    protected ChallengeProblem(String challengeId,
                               String type,
                               ProblemCategory category,
                               String title,
                               String detail,
                               Map<String, Object> context) {
        super(type, category, title, detail, mergeContext(context, challengeId));
        this.challengeId = challengeId;
    }

    protected ChallengeProblem(String challengeId,
                               String type,
                               String title,
                               String detail,
                               Map<String, Object> context) {
        this(challengeId, type, ProblemCategory.BUSINESS_INVALID_COMMAND, title, detail, context);
    }

    protected ChallengeProblem(String challengeId, String type, String title, String detail) {
        this(challengeId, type, ProblemCategory.BUSINESS_INVALID_COMMAND, title, detail, null);
    }

    private static Map<String, Object> mergeContext(Map<String, Object> context, String challengeId) {
        Map<String, Object> merged = new HashMap<>();
        if (context != null) {
            merged.putAll(context);
        }
        merged.put("challengeId", challengeId);
        return merged;
    }

    public String getChallengeId() {
        return challengeId;
    }
}
