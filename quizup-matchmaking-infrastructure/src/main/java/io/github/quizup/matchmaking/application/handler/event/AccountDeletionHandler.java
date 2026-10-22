package io.github.quizup.matchmaking.application.handler.event;

import io.github.quizup.axon.autoconfigure.erasure.AxonEventStoreErasureService;
import io.github.quizup.identity.domain.event.UserEvent.UserAccountDeletedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retire un compte supprimé du pool d'appariement et annule ses salles et défis encore ouverts.
 * Les agrégats éphémères sont purgés par leur rétention habituelle ; les notifications associées
 * sont supprimées par le service notification.
 */
@Component
@ProcessingGroup("account-deletion-matchmaking")
public class AccountDeletionHandler {

    private final JdbcTemplate jdbcTemplate;
    private final AxonEventStoreErasureService eventStoreErasureService;

    public AccountDeletionHandler(JdbcTemplate jdbcTemplate,
                                  AxonEventStoreErasureService eventStoreErasureService) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventStoreErasureService = eventStoreErasureService;
    }

    @EventHandler
    @Transactional
    public void on(UserAccountDeletedEvent event) {
        String userId = event.userId();
        java.util.List<String> roomIds = jdbcTemplate.queryForList("""
                SELECT room_id FROM room_entry
                WHERE initiator_id = ? OR opponent_id = ? OR participant_id = ?
                """, String.class, userId, userId, userId);
        java.util.List<String> challengeIds = jdbcTemplate.queryForList("""
                SELECT challenge_id FROM challenge_entry
                WHERE challenger_id = ? OR opponent_id = ?
                """, String.class, userId, userId);
        jdbcTemplate.update("DELETE FROM matchmaking_entry WHERE player_id = ?", userId);
        jdbcTemplate.update("""
                UPDATE room_entry
                SET status = 'CANCELLED', updated_at = now()
                WHERE status NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED', 'FAILED')
                  AND (initiator_id = ? OR opponent_id = ? OR participant_id = ?)
                """, userId, userId, userId);
        jdbcTemplate.update("""
                UPDATE challenge_entry
                SET status = 'CANCELLED', resolved_at = now()
                WHERE status = 'PENDING' AND (challenger_id = ? OR opponent_id = ?)
                """, userId, userId);
        java.util.List<String> erasedAggregates = new java.util.ArrayList<>(roomIds);
        erasedAggregates.addAll(challengeIds);
        eventStoreErasureService.eraseAggregates(erasedAggregates);
    }
}
