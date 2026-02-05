package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new RuntimeException("El username es obligatorio");
        }
        if (password == null || password.isBlank()) {
            throw new RuntimeException("La password es obligatoria");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Ese username ya existe");
        }

        User u = new User();
        u.setUsername(username.trim());
        u.setPassword(passwordEncoder.encode(password.trim()));
        u.setRole("HOST");

        return userRepository.save(u);
    }

    public User login(String username, String password) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no existe"));

        if (!passwordEncoder.matches(password, u.getPassword())) {
            throw new RuntimeException("Password incorrecta");
        }
        return u;
    }
}
