package io.github.quizup.matchmaking.domain.exception;

import io.github.quizup.microservice.core.domain.exception.ProblemCategory;
import io.github.quizup.microservice.core.domain.model.i18n.Language;

import java.util.Map;
import java.util.Set;

public final class LobbyExceptions {

    private LobbyExceptions() {}

    public static class TopicNotAvailableInLanguageProblem extends LobbyProblem {
        public TopicNotAvailableInLanguageProblem(String topicId, Set<Language> languages) {
            super(topicId, "urn:quizup:lobby:topicNotAvailableInLanguage",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Thème non disponible dans cette langue",
                    "Le thème " + topicId + " n'a pas assez de questions dans " + languages,
                    Map.of("topicId", topicId,
                            "languages", languages.stream().map(Language::code).toList()));
        }
    }

    public static class LobbyNotFoundProblem extends LobbyProblem {
        public LobbyNotFoundProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Salon introuvable", "Le salon " + lobbyId + " n'existe pas", null);
        }
    }

    public static class LobbyNotAvailableProblem extends LobbyProblem {
        public LobbyNotAvailableProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:notAvailable",
                    "Salon non disponible", "Ce salon est déjà fermé ou annulé");
        }
    }

    public static class LobbyAlreadyFullProblem extends LobbyProblem {
        public LobbyAlreadyFullProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:alreadyFull",
                    "Salon complet", "Ce salon a déjà un second participant");
        }
    }

    public static class MissingPlayerIdentifierProblem extends LobbyProblem {
        public MissingPlayerIdentifierProblem(String id) {
            super(id, "urn:quizup:lobby:missingPlayerId",
                    "Identifiant joueur manquant", "Un identifiant joueur valide est requis");
        }
    }

    public static class MissingTopicIdentifierProblem extends LobbyProblem {
        public MissingTopicIdentifierProblem(String id) {
            super(id, "urn:quizup:lobby:missingTopicId",
                    "Identifiant topic manquant", "Un identifiant topic valide est requis");
        }
    }

    public static class MissingGameIdentifierProblem extends LobbyProblem {
        public MissingGameIdentifierProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:missingGameId",
                    "Identifiant partie manquant", "Un identifiant de partie valide est requis");
        }
    }

    public static class PlayerNotInLobbyProblem extends LobbyProblem {
        public PlayerNotInLobbyProblem(String id, String playerId) {
            super(id, "urn:quizup:lobby:playerNotInLobby",
                    "Joueur absent", "Le joueur " + playerId + " n'est pas dans ce salon");
        }
    }

    public static class CannotChallengeSelfProblem extends LobbyProblem {
        public CannotChallengeSelfProblem(String lobbyId, String playerId) {
            super(lobbyId, "urn:quizup:lobby:cannotChallengeSelf",
                    "Défi impossible", "Le joueur " + playerId + " ne peut pas se défier lui-même");
        }
    }

    public static class LobbyNotInvitedProblem extends LobbyProblem {
        public LobbyNotInvitedProblem(String lobbyId, String playerId) {
            super(lobbyId, "urn:quizup:lobby:notInvited",
                    ProblemCategory.PERMISSION,
                    "Défi non adressé",
                    "Le joueur " + playerId + " n'est pas l'invité de ce défi",
                    Map.of("playerId", playerId));
        }
    }

    public static class ParticipantNotPresentProblem extends LobbyProblem {
        public ParticipantNotPresentProblem(String lobbyId) {
            super(lobbyId, "urn:quizup:lobby:participantNotPresent",
                    "Second participant absent", "Un second participant est requis pour clore le salon");
        }
    }

    public static class MatchmakingNotFoundProblem extends LobbyProblem {
        public MatchmakingNotFoundProblem(String matchmakingId) {
            super(matchmakingId, "urn:quizup:matchmaking:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Recherche introuvable", "La recherche " + matchmakingId + " n'existe pas", null);
        }
    }

    public static class MatchmakingNotSearchingProblem extends LobbyProblem {
        public MatchmakingNotSearchingProblem(String matchmakingId, String currentStatus) {
            super(matchmakingId, "urn:quizup:matchmaking:notSearching",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Recherche non active",
                    "La recherche " + matchmakingId + " est en statut " + currentStatus,
                    Map.of("currentStatus", currentStatus));
        }
    }
}
