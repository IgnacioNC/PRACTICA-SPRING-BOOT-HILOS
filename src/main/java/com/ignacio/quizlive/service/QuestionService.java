package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.Block;
import com.ignacio.quizlive.model.Question;
import com.ignacio.quizlive.repository.QuestionRepository;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public Question create(Block block,
                           String statement,
                           String optionA, String optionB, String optionC, String optionD,
                           String correctOption) {
        validate(statement, optionA, optionB, optionC, optionD, correctOption);

        Question q = new Question();
        q.setBlock(block);
        q.setStatement(statement.trim());
        q.setOptionA(optionA.trim());
        q.setOptionB(optionB.trim());
        q.setOptionC(optionC.trim());
        q.setOptionD(optionD.trim());
        q.setCorrectOption(correctOption.trim().toUpperCase());

        return questionRepository.save(q);
    }

    public Question update(Question q,
                           String statement,
                           String optionA, String optionB, String optionC, String optionD,
                           String correctOption) {
        validate(statement, optionA, optionB, optionC, optionD, correctOption);

        q.setStatement(statement.trim());
        q.setOptionA(optionA.trim());
        q.setOptionB(optionB.trim());
        q.setOptionC(optionC.trim());
        q.setOptionD(optionD.trim());
        q.setCorrectOption(correctOption.trim().toUpperCase());

        return questionRepository.save(q);
    }

    public void delete(Question q) {
        questionRepository.delete(q);
    }

    private void validate(String statement,
                          String a, String b, String c, String d,
                          String correct) {

        if (isBlank(statement)) throw new RuntimeException("El enunciado es obligatorio");
        if (isBlank(a) || isBlank(b) || isBlank(c) || isBlank(d))
            throw new RuntimeException("Las 4 opciones deben estar rellenas");

        String opt = correct == null ? "" : correct.trim().toUpperCase();
        if (!(opt.equals("A") || opt.equals("B") || opt.equals("C") || opt.equals("D")))
            throw new RuntimeException("La correcta debe ser A, B, C o D");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public void generateDemoQuestions(Block block, int count) {
        for (int i = 1; i <= count; i++) {
            String statement = "Pregunta demo " + i + " (bloque " + block.getId() + ")";
            String a = "Opcion A";
            String b = "Opcion B";
            String c = "Opcion C";
            String d = "Opcion D";
            String correct = "A";
            create(block, statement, a, b, c, d, correct);
        }
    }

}