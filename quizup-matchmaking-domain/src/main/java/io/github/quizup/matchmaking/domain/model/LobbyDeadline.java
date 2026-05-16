package io.github.quizup.matchmaking.domain.model;

import java.time.Duration;

public interface LobbyDeadline {

    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(10);


    String LOBBY_DEADLINE = "lobby-deadline";

    Duration LOBBY_DEADLINE_DURATION = Duration.ofSeconds(15);
}
