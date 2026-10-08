package io.github.quizup.matchmaking.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Règle de dérivation de la salle temps réel associée à un défi nominatif : l'identifiant est
 * déterministe (rejouabilité) et ne dépend que du {@code challengeId}. Partagée par la saga de
 * création (consommateur) et la projection du défi (read model), pour éviter de stocker ce lien
 * dérivé dans l'agrégat.
 */
public final class ChallengeRoomId {

    private static final String PREFIX = "room:";

    private ChallengeRoomId() {
    }

    /** Identifiant déterministe de la salle créée à l'acceptation d'un défi. */
    public static String of(String challengeId) {
        return UUID.nameUUIDFromBytes((PREFIX + challengeId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
