package com.ignacio.quizlive.controller;

import com.ignacio.quizlive.model.Block;
import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.service.BlockService;
import com.ignacio.quizlive.service.QuestionService;
import com.ignacio.quizlive.repository.QuestionRepository;
import com.ignacio.quizlive.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.ignacio.quizlive.model.Question;

@Controller
@RequestMapping("/blocks")
public class BlockController {

    private final BlockService blockService;
    private final CurrentUserService currentUserService;
    private final QuestionService questionService;
    private final QuestionRepository questionRepository;

    public BlockController(BlockService blockService, CurrentUserService currentUserService, 
                        QuestionRepository questionRepository, QuestionService questionService) {
        this.blockService = blockService;
        this.currentUserService = currentUserService;
        this.questionRepository = questionRepository;
        this.questionService = questionService;
    }

    private User me() {
        return currentUserService.getCurrentUser();
    }

    // LISTAR + FORM CREAR
    @GetMapping
    public String list(Model model) {
        model.addAttribute("blocks", blockService.getBlocksByUser(me()));
        return "blocks/list";
    }

    // CREAR BLOQUE
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         Model model) {
        try {
            blockService.createBlock(name, description, me());
            return "redirect:/blocks";
        } catch (RuntimeException ex) {
            model.addAttribute("blocks", blockService.getBlocksByUser(me()));
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("name", name);
            model.addAttribute("description", description);
            return "blocks/list";
        }
    }

    // VER DETALLE DEL BLOQUE (y sus preguntas)
    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        Block block = blockService.getMyBlockById(me(), id);
        model.addAttribute("block", block);
        model.addAttribute("questions", block.getQuestions());
        model.addAttribute("usable", blockService.canBeUsedInRoom(block));
        return "blocks/details";
    }

    // FORM EDITAR
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Block block = blockService.getMyBlockById(me(), id);
        model.addAttribute("block", block);
        return "blocks/edit";
    }

    // GUARDAR EDITAR
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
                       Model model) {
        try {
            blockService.updateBlock(me(), id, name, description);
            return "redirect:/blocks";
        } catch (RuntimeException ex) {
            Block block = blockService.getMyBlockById(me(), id);
            model.addAttribute("block", block);
            model.addAttribute("error", ex.getMessage());
            return "blocks/edit";
        }
    }

    // BORRAR
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Model model) {
        try {
            blockService.deleteBlock(me(), id);
            return "redirect:/blocks";
        } catch (RuntimeException ex) {
            model.addAttribute("blocks", blockService.getBlocksByUser(me()));
            model.addAttribute("error", ex.getMessage());
            return "blocks/list";
        }
    }

    @GetMapping("/{bid}/questions/new")
    public String newQuestionForm(@PathVariable Long bid, Model model) {
        Block block = blockService.getMyBlockById(me(), bid);
        model.addAttribute("block", block);
        return "questions/new";
    }

    @PostMapping("/{bid}/questions")
    public String createQuestion(@PathVariable Long bid,
                                @RequestParam String statement,
                                @RequestParam String optionA,
                                @RequestParam String optionB,
                                @RequestParam String optionC,
                                @RequestParam String optionD,
                                @RequestParam String correctOption,
                                Model model) {
        Block block = blockService.getMyBlockById(me(), bid);

        try {
            questionService.create(block, statement, optionA, optionB, optionC, optionD, correctOption);
            return "redirect:/blocks/" + bid;
        } catch (RuntimeException ex) {
            model.addAttribute("block", block);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("statement", statement);
            model.addAttribute("optionA", optionA);
            model.addAttribute("optionB", optionB);
            model.addAttribute("optionC", optionC);
            model.addAttribute("optionD", optionD);
            model.addAttribute("correctOption", correctOption);
            return "questions/new";
        }
    }

    @GetMapping("/{bid}/questions/{qid}/edit")
    public String editQuestionForm(@PathVariable Long bid, @PathVariable Long qid, Model model) {
        Block block = blockService.getMyBlockById(me(), bid);

        Question q = questionRepository.findById(qid)
                .orElseThrow(() -> new RuntimeException("Pregunta no encontrada"));

        if (!q.getBlock().getId().equals(block.getId())) {
            throw new RuntimeException("La pregunta no pertenece a este bloque");
        }

        model.addAttribute("block", block);
        model.addAttribute("q", q);
        return "questions/edit";
    }

    @PostMapping("/{bid}/questions/{qid}/edit")
    public String editQuestion(@PathVariable Long bid, @PathVariable Long qid,
                            @RequestParam String statement,
                            @RequestParam String optionA,
                            @RequestParam String optionB,
                            @RequestParam String optionC,
                            @RequestParam String optionD,
                            @RequestParam String correctOption,
                            Model model) {
        Block block = blockService.getMyBlockById(me(), bid);

        Question q = questionRepository.findById(qid)
                .orElseThrow(() -> new RuntimeException("Pregunta no encontrada"));

        if (!q.getBlock().getId().equals(block.getId())) {
            throw new RuntimeException("La pregunta no pertenece a este bloque");
        }

        try {
            questionService.update(q, statement, optionA, optionB, optionC, optionD, correctOption);
            return "redirect:/blocks/" + bid;
        } catch (RuntimeException ex) {
            model.addAttribute("block", block);
            model.addAttribute("q", q);
            model.addAttribute("error", ex.getMessage());
            return "questions/edit";
        }
    }

    @PostMapping("/{bid}/questions/{qid}/delete")
    public String deleteQuestion(@PathVariable Long bid, @PathVariable Long qid) {
        Block block = blockService.getMyBlockById(me(), bid);

        Question q = questionRepository.findById(qid)
                .orElseThrow(() -> new RuntimeException("Pregunta no encontrada"));

        if (!q.getBlock().getId().equals(block.getId())) {
            throw new RuntimeException("La pregunta no pertenece a este bloque");
        }

        questionService.delete(q);
        return "redirect:/blocks/" + bid;
    }

    @PostMapping("/{id}/questions/demo")
    public String generateDemo(@PathVariable Long id) {
        Block block = blockService.getMyBlockById(me(), id);
        int current = (block.getQuestions() == null) ? 0 : block.getQuestions().size();
        int needed = Math.max(0, 20 - current);

        if (needed > 0) {
            questionService.generateDemoQuestions(block, needed);
        }

        return "redirect:/blocks/" + id;
    }


}
