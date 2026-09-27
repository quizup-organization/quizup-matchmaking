package io.github.quizup.matchmaking.domain.query;

/**
 * Queries du read model « ticket » de matchmaking.
 */
public interface TicketQuery {

    /**
     * Retrouve le ticket par id (vide si inconnu ou purgé).
     */
    record FindTicketByIdQuery(String ticketId) implements TicketQuery {
    }
}
