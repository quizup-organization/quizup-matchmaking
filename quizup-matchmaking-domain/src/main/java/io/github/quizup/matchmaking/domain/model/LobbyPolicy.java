package io.github.quizup.matchmaking.domain.model;

import io.github.quizup.microservice.core.domain.constant.QuizUpConstants;

/**
 * Règles métier du cycle de vie d'un lobby.
 *
 * <p>Le fallback bot est <b>automatique</b> (pas d'attente infinie qui consomme des ressources) :
 * à l'échéance d'attente, un bot rejoint si aucun humain n'est arrivé et que l'initiateur est
 * toujours en ligne ; sinon le lobby est annulé (initiateur parti).</p>
 */
public final class LobbyPolicy {

    private LobbyPolicy() {}

    public static final String BOT_PLAYER_ID = QuizUpConstants.SYSTEM_USER_ID;

    /** Un bot rejoint quand personne n'est arrivé et que l'initiateur est toujours en ligne. */
    public static boolean shouldFallbackToBot(String challengerId, boolean initiatorOnline) {
        return noChallenger(challengerId) && initiatorOnline;
    }

    /** Le lobby est abandonné (initiateur hors ligne) : à annuler plutôt qu'à peupler d'un bot. */
    public static boolean shouldCancelOffline(String challengerId, boolean initiatorOnline) {
        return noChallenger(challengerId) && !initiatorOnline;
    }

    private static boolean noChallenger(String challengerId) {
        return challengerId == null || challengerId.isBlank();
    }
}
