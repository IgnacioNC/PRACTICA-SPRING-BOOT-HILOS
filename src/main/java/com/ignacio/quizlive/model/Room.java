package com.ignacio.quizlive.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String pin;

    @ManyToOne(optional = false)
    private User host;

    @ManyToOne(optional = false)
    private Block block;

    @Column(nullable = false)
    private int questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SelectionMode selectionMode;

    @Column(nullable = false)
    private int timePerQuestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdvanceMode advanceMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomState state;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    private Integer currentQuestionIndex;

    private LocalDateTime questionStartedAt;

    @Enumerated(EnumType.STRING)
    private RoomPhase phase;

    private LocalDateTime phaseStartedAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public SelectionMode getSelectionMode() { return selectionMode; }
    public void setSelectionMode(SelectionMode selectionMode) { this.selectionMode = selectionMode; }

    public Integer getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(Integer currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public LocalDateTime getQuestionStartedAt() { return questionStartedAt; }
    public void setQuestionStartedAt(LocalDateTime questionStartedAt) { this.questionStartedAt = questionStartedAt; }

    public RoomPhase getPhase() { return phase; }
    public void setPhase(RoomPhase phase) { this.phase = phase; }

    public LocalDateTime getPhaseStartedAt() { return phaseStartedAt; }
    public void setPhaseStartedAt(LocalDateTime phaseStartedAt) { this.phaseStartedAt = phaseStartedAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastActivityAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public User getHost() { return host; }
    public void setHost(User host) { this.host = host; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }

    public int getQuestionCount() { return questionCount; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }

    public int getTimePerQuestion() { return timePerQuestion; }
    public void setTimePerQuestion(int timePerQuestion) { this.timePerQuestion = timePerQuestion; }

    public AdvanceMode getAdvanceMode() { return advanceMode; }
    public void setAdvanceMode(AdvanceMode advanceMode) { this.advanceMode = advanceMode; }

    public RoomState getState() { return state; }
    public void setState(RoomState state) { this.state = state; }
}
