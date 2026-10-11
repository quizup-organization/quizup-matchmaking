package io.github.quizup.matchmaking.domain.model;

/**
 * Statut d'une salle : cycle de vie et avancement de la préparation de la partie.
 * <ul>
 *   <li>{@link #WAITING_PLAYERS} — ouverte, en attente de la présence des deux joueurs
 *       (sortie/rejointure autorisées).</li>
 *   <li>{@link #FETCHING_PLAYERS} — les deux joueurs sont présents, récupération des profils
 *       (sortie refusée : le duel est engagé).</li>
 *   <li>{@link #FETCHING_PLAYED_QUESTIONS} — profils résolus, récupération des questions déjà
 *       jouées par les joueurs (quizup-game) pour éviter les répétitions.</li>
 *   <li>{@link #FETCHING_QUESTIONS} — historique résolu, tirage des questions (préférences :
 *       jamais jouées puis difficulté déduite du niveau).</li>
 *   <li>{@link #CREATING_GAME} — questions prêtes, la partie est créée immédiatement.</li>
 *   <li>{@link #GAME_CREATED} — la partie est créée (accusé du service game) ; la salle peut
 *       être clôturée.</li>
 *   <li>{@link #COMPLETED} — partie créée ({@code gameId} porté par l'événement terminal).</li>
 *   <li>{@link #CANCELLED} — salle annulée par l'initiateur.</li>
 *   <li>{@link #EXPIRED} — salle jamais lancée, expirée.</li>
 *   <li>{@link #FAILED} — préparation ou création impossible.</li>
 * </ul>
 * L'annulation par l'initiateur reste possible tant que la salle n'est pas terminale.
 * Aucune suppression immédiate : un état terminal est purgé par la saga après rétention
 * (deadline), puis {@code markDeleted} (standard Axon).
 */
public enum RoomStatus {
    WAITING_PLAYERS,
    FETCHING_PLAYERS,
    FETCHING_PLAYED_QUESTIONS,
    FETCHING_QUESTIONS,
    CREATING_GAME,
    GAME_CREATED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    FAILED
}
