package com.ignacio.quizlive.model;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String statement;

    @Column(nullable=false)
    private String optionA;

    @Column(nullable=false)
    private String optionB;

    @Column(nullable=false)
    private String optionC;

    @Column(nullable=false)
    private String optionD;

    @Column(nullable=false)
    private String correctOption; // "A","B","C","D"

    @ManyToOne(optional=false)
    private Block block;

    public Long getId() { return id; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }

    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }

    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }

    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }

    public String getCorrectOption() { return correctOption; }
    public void setCorrectOption(String correctOption) { this.correctOption = correctOption; }

    public Block getBlock() { return block; }
    public void setBlock(Block block) { this.block = block; }
}