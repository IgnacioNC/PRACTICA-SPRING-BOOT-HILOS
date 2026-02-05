package com.ignacio.quizlive.repository;

import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.model.RoomState;
import com.ignacio.quizlive.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByPin(String pin);

    Optional<Room> findByPin(String pin);

    List<Room> findByHost(User host);

    List<Room> findByStateAndLastActivityAtBefore(RoomState state, LocalDateTime limit);

    long countByBlock(Block block);
}
