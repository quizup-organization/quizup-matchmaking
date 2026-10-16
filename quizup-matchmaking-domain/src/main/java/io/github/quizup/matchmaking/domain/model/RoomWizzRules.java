package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

/**
 * Règles de la relance (« wizz ») : un joueur présent demande à son adversaire absent de
 * rejoindre la salle. La cadence par joueur borne le harcèlement sans jamais bloquer l'état de la
 * salle (pur temps réel, aucune préparation déclenchée).
 */
public interface RoomWizzRules {

    /** Délai minimal entre deux relances d'un même joueur dans la même salle. */
    Duration WIZZ_COOLDOWN_DURATION = Duration.ofSeconds(10);
}
