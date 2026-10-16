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

    /** Une fois la préparation engagée (les deux présents), la salle ne peut plus être quittée. */
    public static class RoomPreparationStartedProblem extends RoomProblem {
        public RoomPreparationStartedProblem(String roomId) {
            super(roomId, "urn:quizup:room:preparationStarted",
                    "Préparation engagée",
                    "La salle ne peut plus être quittée une fois la préparation de la partie lancée");
        }
    }

    /** La relance exige un joueur réellement présent dans la salle (« connecté en attente »). */
    public static class RoomWizzRequesterNotPresentProblem extends RoomProblem {
        public RoomWizzRequesterNotPresentProblem(String roomId, String playerId) {
            super(roomId, "urn:quizup:room:wizzRequesterNotPresent",
                    "Joueur non présent",
                    "Le joueur " + playerId + " doit être présent dans la salle pour relancer son adversaire",
                    Map.of("playerId", playerId));
        }
    }

    /**
     * Rien à relancer : salle partagée sans invité nommé, adversaire déjà présent ou adversaire
     * système (bot).
     */
    public static class RoomWizzUnavailableProblem extends RoomProblem {
        public RoomWizzUnavailableProblem(String roomId) {
            super(roomId, "urn:quizup:room:wizzUnavailable",
                    "Aucun adversaire à relancer",
                    "Cette salle n'a pas d'adversaire humain absent à relancer");
        }
    }

    /** Cadence anti-spam : un même joueur ne peut pas relancer avant la fin du délai. */
    public static class RoomWizzCooldownProblem extends RoomProblem {
        public RoomWizzCooldownProblem(String roomId, String playerId) {
            super(roomId, "urn:quizup:room:wizzCooldown",
                    "Relance trop rapide",
                    "Le joueur " + playerId + " vient déjà de relancer son adversaire",
                    Map.of("playerId", playerId));
        }
    }
}
