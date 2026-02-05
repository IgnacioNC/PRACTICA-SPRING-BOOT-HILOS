package com.ignacio.quizlive.controller;

import com.ignacio.quizlive.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CurrentUserService currentUserService;

    public HomeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/")
    public String home() {
        if (currentUserService.getCurrentUserOrNull() == null) {
            return "redirect:/login";
        }
        return "redirect:/blocks";
    }
}