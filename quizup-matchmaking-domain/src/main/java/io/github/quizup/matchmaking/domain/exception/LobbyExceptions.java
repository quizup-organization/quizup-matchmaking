package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.ProblemCategory;

public final class LobbyExceptions {

    private LobbyExceptions() {}

    public static class LobbyNotFoundProblem extends LobbyProblem {
        public LobbyNotFoundProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Lobby introuvable", "Le lobby " + lobbyId + " n'existe pas", null);
        }
    }

    public static class LobbyNotAvailableProblem extends LobbyProblem {
        public LobbyNotAvailableProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:notAvailable",
                    "Lobby non disponible", "Ce lobby est déjà fermé ou annulé");
        }
    }

    public static class LobbyAlreadyFullProblem extends LobbyProblem {
        public LobbyAlreadyFullProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:alreadyFull",
                    "Lobby complet", "Ce lobby a déjà un challenger");
        }
    }

    public static class LobbyAlreadyClosedProblem extends LobbyProblem {
        public LobbyAlreadyClosedProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:alreadyClosed",
                    "Lobby déjà fermé", "Une partie a déjà été créée pour ce lobby");
        }
    }

    public static class LobbyAlreadyCancelledProblem extends LobbyProblem {
        public LobbyAlreadyCancelledProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:alreadyCancelled",
                    "Lobby déjà annulé", "Ce lobby a déjà été annulé");
        }
    }

    public static class MissingPlayerIdentifierProblem extends LobbyProblem {
        public MissingPlayerIdentifierProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:missingPlayerId",
                    "Identifiant joueur manquant", "Un identifiant joueur valide est requis");
        }
    }

    public static class MissingTopicIdentifierProblem extends LobbyProblem {
        public MissingTopicIdentifierProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:missingTopicId",
                    "Identifiant topic manquant", "Un identifiant topic valide est requis");
        }
    }

    public static class MissingGameIdentifierProblem extends LobbyProblem {
        public MissingGameIdentifierProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:missingGameId",
                    "Identifiant partie manquant", "Un identifiant de partie valide est requis");
        }
    }

    public static class PlayerAlreadyInLobbyProblem extends LobbyProblem {
        public PlayerAlreadyInLobbyProblem(String lobbyId, String playerId) {
            super(lobbyId, "urn:quizup:lobby:playerAlreadyInLobby",
                    "Joueur déjà présent", "Le joueur " + playerId + " est déjà dans ce lobby");
        }
    }

    public static class PlayerNotInLobbyProblem extends LobbyProblem {
        public PlayerNotInLobbyProblem(String lobbyId, String playerId) {
            super(lobbyId, "urn:quizup:lobby:playerNotInLobby",
                    "Joueur absent", "Le joueur " + playerId + " n'est pas dans ce lobby");
        }
    }

    public static class ChallengerNotPresentProblem extends LobbyProblem {
        public ChallengerNotPresentProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:challengerNotPresent",
                    "Challenger absent", "Un challenger est requis pour fermer le lobby");
        }
    }

}
