package com.ignacio.quizlive.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "answers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "room_question_id"}))
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Player player;

    @ManyToOne(optional = false)
    private RoomQuestion roomQuestion;

    @Column(nullable = false)
    private String selectedOption;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    public void onCreate() {
        this.answeredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public RoomQuestion getRoomQuestion() { return roomQuestion; }
    public void setRoomQuestion(RoomQuestion roomQuestion) { this.roomQuestion = roomQuestion; }

    public String getSelectedOption() { return selectedOption; }
    public void setSelectedOption(String selectedOption) { this.selectedOption = selectedOption; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }
}