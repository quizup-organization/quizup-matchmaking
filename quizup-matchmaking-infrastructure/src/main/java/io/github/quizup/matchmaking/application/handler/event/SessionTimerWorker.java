package io.github.quizup.matchmaking.application.handler.event;

import io.github.quizup.matchmaking.application.service.ChallengeService;
import io.github.quizup.matchmaking.application.service.LobbyService;
import io.github.quizup.matchmaking.application.service.MatchmakingService;
import io.github.quizup.matchmaking.domain.model.SessionTimer;
import io.github.quizup.matchmaking.domain.port.out.SessionTimerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Worker des timers du session tier : réclame atomiquement les échéances dépassées (ZSET Redis)
 * et les dispatche. Chaque timer est retiré avant dispatch (une seule instance le tire) ; le
 * handler re-vérifie l'état dans le store (un timer obsolète est un no-op).
 */
@Component
public class SessionTimerWorker {

    private static final Logger logger = LoggerFactory.getLogger(SessionTimerWorker.class);
    private static final int BATCH_SIZE = 100;

    private final SessionTimerPort timerPort;
    private final MatchmakingService matchmakingService;
    private final LobbyService lobbyService;
    private final ChallengeService challengeService;

    public SessionTimerWorker(SessionTimerPort timerPort,
                              MatchmakingService matchmakingService,
                              LobbyService lobbyService,
                              ChallengeService challengeService) {
        this.timerPort = timerPort;
        this.matchmakingService = matchmakingService;
        this.lobbyService = lobbyService;
        this.challengeService = challengeService;
    }

    @Scheduled(fixedDelayString = "${app.session.timer-interval-ms:500}")
    public void tick() {
        try {
            List<SessionTimer.Due> due = timerPort.claimDue(Instant.now(), BATCH_SIZE);
            for (SessionTimer.Due timer : due) {
                dispatch(timer);
            }
        } catch (Exception exception) {
            logger.error("Échec du worker de timers de session", exception);
        }
    }

    private void dispatch(SessionTimer.Due timer) {
        switch (timer.type()) {
            case SessionTimer.MATCHMAKING_DEADLINE -> matchmakingService.onDeadline(timer.referenceId());
            case SessionTimer.LOBBY_READY_CHECK -> lobbyService.onReadyCheck(timer.referenceId());
            case SessionTimer.LOBBY_EXPIRY -> lobbyService.expire(timer.referenceId());
            case SessionTimer.CHALLENGE_EXPIRY -> challengeService.expire(timer.referenceId());
            default -> logger.warn("Timer sans handler: type={}, ref={}", timer.type(), timer.referenceId());
        }
    }
}
