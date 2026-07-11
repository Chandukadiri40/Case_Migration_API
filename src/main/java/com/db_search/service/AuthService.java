package com.db_search.service;

import com.db_search.entity.User;
import com.db_search.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean authenticate(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        Optional<User> userOpt = userRepository.findById(username.trim());
        if (userOpt.isPresent()) {
            return password.equals(userOpt.get().getPassword());
        }
        return false;
    }
}
