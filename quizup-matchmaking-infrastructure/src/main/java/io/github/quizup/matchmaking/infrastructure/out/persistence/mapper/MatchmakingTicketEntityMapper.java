package io.github.quizup.matchmaking.infrastructure.out.persistence.mapper;

import io.github.quizup.matchmaking.domain.model.MatchmakingTicket;
import io.github.quizup.matchmaking.infrastructure.out.persistence.entity.MatchmakingTicketEntity;

public final class MatchmakingTicketEntityMapper {

    private MatchmakingTicketEntityMapper() {
    }

    public static MatchmakingTicket toDomain(MatchmakingTicketEntity entity) {
        return MatchmakingTicket.builder()
                .ticketId(entity.getTicketId())
                .topicId(entity.getTopicId())
                .initiatorId(entity.getInitiatorId())
                .opponentId(entity.getOpponentId())
                .gameId(entity.getGameId())
                .vsBot(entity.isVsBot())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static MatchmakingTicketEntity toEntity(MatchmakingTicket ticket) {
        MatchmakingTicketEntity entity = new MatchmakingTicketEntity();
        entity.setTicketId(ticket.ticketId());
        entity.setTopicId(ticket.topicId());
        entity.setInitiatorId(ticket.initiatorId());
        entity.setOpponentId(ticket.opponentId());
        entity.setGameId(ticket.gameId());
        entity.setVsBot(ticket.vsBot());
        entity.setStatus(ticket.status());
        entity.setCreatedAt(ticket.createdAt());
        entity.setUpdatedAt(ticket.updatedAt());
        return entity;
    }
}
