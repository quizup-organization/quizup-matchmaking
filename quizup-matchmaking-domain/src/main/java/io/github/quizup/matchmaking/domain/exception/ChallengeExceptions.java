package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.ProblemCategory;

import java.util.Map;

public final class ChallengeExceptions {

    private ChallengeExceptions() {
    }

    public static class ChallengeNotFoundProblem extends ChallengeProblem {
        public ChallengeNotFoundProblem(String challengeId) {
            super(challengeId, "urn:quizup:challenge:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Défi introuvable", "Le défi " + challengeId + " n'existe pas", null);
        }
    }

    public static class ChallengeNotPendingProblem extends ChallengeProblem {
        public ChallengeNotPendingProblem(String challengeId, String status) {
            super(challengeId, "urn:quizup:challenge:notPending",
                    "Défi déjà résolu",
                    "Le défi " + challengeId + " est en statut " + status,
                    Map.of("status", status));
        }
    }

    public static class ChallengeNotInvitedProblem extends ChallengeProblem {
        public ChallengeNotInvitedProblem(String challengeId, String playerId) {
            super(challengeId, "urn:quizup:challenge:notInvited",
                    ProblemCategory.PERMISSION,
                    "Défi non adressé",
                    "Le joueur " + playerId + " n'est pas l'invité de ce défi",
                    Map.of("playerId", playerId));
        }
    }

    public static class PlayerNotChallengerProblem extends ChallengeProblem {
        public PlayerNotChallengerProblem(String challengeId, String playerId) {
            super(challengeId, "urn:quizup:challenge:notChallenger",
                    ProblemCategory.PERMISSION,
                    "Annulation refusée",
                    "Seul le lanceur du défi peut l'annuler",
                    Map.of("playerId", playerId));
        }
    }

    public static class CannotChallengeSelfProblem extends ChallengeProblem {
        public CannotChallengeSelfProblem(String challengeId, String playerId) {
            super(challengeId, "urn:quizup:challenge:cannotChallengeSelf",
                    "Défi impossible", "Le joueur " + playerId + " ne peut pas se défier lui-même");
        }
    }

    public static class MissingChallengerIdentifierProblem extends ChallengeProblem {
        public MissingChallengerIdentifierProblem(String challengeId) {
            super(challengeId, "urn:quizup:challenge:missingChallengerId",
                    "Identifiant joueur manquant", "Un identifiant de lanceur valide est requis");
        }
    }

    public static class MissingOpponentIdentifierProblem extends ChallengeProblem {
        public MissingOpponentIdentifierProblem(String challengeId) {
            super(challengeId, "urn:quizup:challenge:missingOpponentId",
                    "Identifiant adversaire manquant", "Un identifiant d'adversaire valide est requis");
        }
    }

    public static class MissingTopicIdentifierProblem extends ChallengeProblem {
        public MissingTopicIdentifierProblem(String challengeId) {
            super(challengeId, "urn:quizup:challenge:missingTopicId",
                    "Identifiant topic manquant", "Un identifiant topic valide est requis");
        }
    }

}
