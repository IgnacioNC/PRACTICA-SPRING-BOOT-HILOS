package com.ignacio.quizlive.repository;

import com.ignacio.quizlive.model.Player;
import com.ignacio.quizlive.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByRoomAndName(Room room, String name);

    List<Player> findByRoomOrderByJoinedAtAsc(Room room);

    List<Player> findByRoomOrderByScoreDescNameAsc(Room room);

    long countByRoom(Room room);

    long countByRoomAndLastSeenAtAfter(Room room, java.time.LocalDateTime after);

    void deleteByRoom(Room room);
}
