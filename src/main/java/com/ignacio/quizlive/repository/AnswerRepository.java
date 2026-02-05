package com.ignacio.quizlive.repository;

import com.ignacio.quizlive.model.Answer;
import com.ignacio.quizlive.model.Player;
import com.ignacio.quizlive.model.RoomQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    boolean existsByPlayerAndRoomQuestion(Player player, RoomQuestion roomQuestion);

    java.util.Optional<Answer> findByPlayerAndRoomQuestion(Player player, RoomQuestion roomQuestion);

    java.util.List<Answer> findByRoomQuestion(RoomQuestion roomQuestion);

    long countByRoomQuestion(RoomQuestion roomQuestion);

    void deleteByRoomQuestionRoom(com.ignacio.quizlive.model.Room room);
}
