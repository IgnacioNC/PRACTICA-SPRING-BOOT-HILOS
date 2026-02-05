package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        User u = getCurrentUserOrNull();
        if (u == null) {
            throw new RuntimeException("Debes iniciar sesion");
        }
        return u;
    }

    public User getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        }
        if (principal instanceof String username) {
            if ("anonymousUser".equals(username)) return null;
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        }
        return null;
    }

    public void setCurrentUser(User user) {
        throw new UnsupportedOperationException("setCurrentUser no se usa con Spring Security");
    }

    public void logout() {
        throw new UnsupportedOperationException("logout se gestiona en Spring Security");
    }
}
