package com.ignacio.quizlive.controller;

import com.ignacio.quizlive.service.AuthService;
import com.ignacio.quizlive.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/login")
    public String loginForm() {
        if (currentUserService.getCurrentUserOrNull() != null) {
            return "redirect:/blocks";
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm() {
        if (currentUserService.getCurrentUserOrNull() != null) {
            return "redirect:/blocks";
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           Model model) {
        try {
            authService.register(username, password);
            return "redirect:/login?registered";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("username", username);
            return "auth/register";
        }
    }
}
