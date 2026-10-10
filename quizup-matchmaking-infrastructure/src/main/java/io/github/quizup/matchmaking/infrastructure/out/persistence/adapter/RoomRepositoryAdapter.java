package io.github.quizup.matchmaking.infrastructure.out.persistence.adapter;

import io.github.quizup.matchmaking.domain.model.Room;
import io.github.quizup.matchmaking.domain.model.RoomStatus;
import io.github.quizup.matchmaking.domain.port.out.RoomRepositoryPort;
import io.github.quizup.matchmaking.infrastructure.out.persistence.mapper.RoomEntityMapper;
import io.github.quizup.matchmaking.infrastructure.out.persistence.repository.RoomJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class RoomRepositoryAdapter implements RoomRepositoryPort {

    private final RoomJpaRepository roomJpaRepository;

    public RoomRepositoryAdapter(RoomJpaRepository roomJpaRepository) {
        this.roomJpaRepository = roomJpaRepository;
    }

    @Override
    @Transactional
    public void save(Room room) {
        roomJpaRepository.save(RoomEntityMapper.toEntity(room));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room> findById(String roomId) {
        return roomJpaRepository.findById(roomId).map(RoomEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> findCreatedByPlayerId(String playerId) {
        return roomJpaRepository
                .findCreatedByPlayerId(RoomStatus.CREATED, playerId)
                .stream()
                .map(RoomEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(String roomId) {
        roomJpaRepository.deleteById(roomId);
    }
}
