package io.github.quizup.matchmaking.domain.port.out;

/**
 * Port sortant des KPI métier du matchmaking.
 *
 * <p>Implémenté en infrastructure avec Micrometer. Types JDK uniquement (règle hexagonale).
 */
public interface MatchmakingMetricsPort {

    /** Un lobby a été ouvert. */
    void lobbyOpened(String topicId);

    /** Un joueur a rejoint un lobby. */
    void lobbyJoined(String challengerType);

    /** Un lobby a été annulé par son initiateur. */
    void lobbyCancelled();

    /**
     * Un match a été formé.
     *
     * @param topicId thème du lobby
     * @param vsBot   adversaire bot (fallback) plutôt qu'humain
     * @param waitMs  temps d'attente entre ouverture et appariement (négatif si inconnu)
     */
    void matchFound(String topicId, boolean vsBot, long waitMs);

    /** Un lobby a été purgé (rétention). */
    void lobbyPurged();
}
