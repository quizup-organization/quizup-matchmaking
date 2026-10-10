package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.BaseProblem;
import io.github.quizup.microservice.core.domain.exception.ProblemCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Problèmes métier de l'appariement public (tickets). Le contexte porte {@code matchmakingId}.
 */
public final class MatchmakingExceptions {

    private MatchmakingExceptions() {}

    private abstract static class MatchmakingProblem extends BaseProblem {

        private final String matchmakingId;

        protected MatchmakingProblem(
                String matchmakingId,
                String type,
                ProblemCategory category,
                String title,
                String detail,
                Map<String, Object> context) {
            super(type, category, title, detail, mergeContext(context, matchmakingId));
            this.matchmakingId = matchmakingId;
        }

        public String getMatchmakingId() {
            return matchmakingId;
        }

        private static Map<String, Object> mergeContext(Map<String, Object> context, String matchmakingId) {
            Map<String, Object> merged = new HashMap<>();
            if (context != null) {
                merged.putAll(context);
            }
            merged.put("matchmakingId", matchmakingId);
            return merged;
        }
    }

    public static class MissingPlayerIdentifierProblem extends MatchmakingProblem {
        public MissingPlayerIdentifierProblem(String matchmakingId) {
            super(matchmakingId, "urn:quizup:matchmaking:missingPlayerId",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Identifiant joueur manquant", "Un identifiant joueur valide est requis", null);
        }
    }

    public static class MissingTopicIdentifierProblem extends MatchmakingProblem {
        public MissingTopicIdentifierProblem(String matchmakingId) {
            super(matchmakingId, "urn:quizup:matchmaking:missingTopicId",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Identifiant topic manquant", "Un identifiant topic valide est requis", null);
        }
    }

    public static class PlayerNotInMatchmakingProblem extends MatchmakingProblem {
        public PlayerNotInMatchmakingProblem(String matchmakingId, String playerId) {
            super(matchmakingId, "urn:quizup:matchmaking:playerNotInMatchmaking",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Joueur absent", "Le joueur " + playerId + " n'a pas cette recherche",
                    Map.of("playerId", playerId));
        }
    }

    public static class MatchmakingNotFoundProblem extends MatchmakingProblem {
        public MatchmakingNotFoundProblem(String matchmakingId) {
            super(matchmakingId, "urn:quizup:matchmaking:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Recherche introuvable", "La recherche " + matchmakingId + " n'existe pas", null);
        }
    }

    public static class MatchmakingNotSearchingProblem extends MatchmakingProblem {
        public MatchmakingNotSearchingProblem(String matchmakingId, String currentStatus) {
            super(matchmakingId, "urn:quizup:matchmaking:notSearching",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Recherche non active",
                    "La recherche " + matchmakingId + " est en statut " + currentStatus,
                    Map.of("currentStatus", currentStatus));
        }
    }
}
