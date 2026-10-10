package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.ProblemCategory;

import java.util.Map;

public final class RoomExceptions {

    private RoomExceptions() {}

    public static class RoomNotFoundProblem extends RoomProblem {
        public RoomNotFoundProblem(String roomId) {
            super(roomId, "urn:quizup:room:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Salle introuvable", "La salle " + roomId + " n'existe pas", null);
        }
    }

    public static class RoomNotAvailableProblem extends RoomProblem {
        public RoomNotAvailableProblem(String roomId) {
            super(roomId, "urn:quizup:room:notAvailable",
                    "Salle non disponible", "Cette salle est déjà fermée ou annulée");
        }
    }

    public static class RoomAlreadyFullProblem extends RoomProblem {
        public RoomAlreadyFullProblem(String roomId) {
            super(roomId, "urn:quizup:room:alreadyFull",
                    "Salle complète", "Cette salle a déjà un second participant");
        }
    }

    public static class MissingPlayerIdentifierProblem extends RoomProblem {
        public MissingPlayerIdentifierProblem(String roomId) {
            super(roomId, "urn:quizup:room:missingPlayerId",
                    "Identifiant joueur manquant", "Un identifiant joueur valide est requis");
        }
    }

    public static class MissingTopicIdentifierProblem extends RoomProblem {
        public MissingTopicIdentifierProblem(String roomId) {
            super(roomId, "urn:quizup:room:missingTopicId",
                    "Identifiant topic manquant", "Un identifiant topic valide est requis");
        }
    }

    public static class MissingGameIdentifierProblem extends RoomProblem {
        public MissingGameIdentifierProblem(String roomId) {
            super(roomId, "urn:quizup:room:missingGameId",
                    "Identifiant partie manquant", "Un identifiant de partie valide est requis");
        }
    }

    public static class PlayerNotInRoomProblem extends RoomProblem {
        public PlayerNotInRoomProblem(String roomId, String playerId) {
            super(roomId, "urn:quizup:room:playerNotInRoom",
                    "Joueur absent", "Le joueur " + playerId + " n'est pas dans cette salle");
        }
    }

    public static class RoomNotInvitedProblem extends RoomProblem {
        public RoomNotInvitedProblem(String roomId, String playerId) {
            super(roomId, "urn:quizup:room:notInvited",
                    ProblemCategory.PERMISSION,
                    "Défi non adressé",
                    "Le joueur " + playerId + " n'est pas l'invité de ce défi",
                    Map.of("playerId", playerId));
        }
    }

    public static class ParticipantNotPresentProblem extends RoomProblem {
        public ParticipantNotPresentProblem(String roomId) {
            super(roomId, "urn:quizup:room:participantNotPresent",
                    "Second participant absent", "Un second participant est requis pour clore la salle");
        }
    }
}
