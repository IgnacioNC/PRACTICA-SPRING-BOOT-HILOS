package com.ignacio.quizlive.repository;

import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.RoomQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RoomQuestionRepository extends JpaRepository<RoomQuestion, Long> {

    List<RoomQuestion> findByRoomOrderByOrderIndexAsc(Room room);

    Optional<RoomQuestion> findByRoomAndOrderIndex(Room room, int orderIndex);

    @Modifying
    @Transactional
    void deleteByRoom(Room room);

    long countByRoom(Room room);
}