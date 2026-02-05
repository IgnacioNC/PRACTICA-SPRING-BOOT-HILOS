package com.ignacio.quizlive.model;

import jakarta.persistence.*;

@Entity
@Table(name = "room_questions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "question_id"}))
public class RoomQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Room room;

    @ManyToOne(optional = false)
    private Question question;

    @Column(nullable = false)
    private int orderIndex; // 1..X (orden de juego)

    public Long getId() { return id; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}