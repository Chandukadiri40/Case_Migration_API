package com.migrationreport.service;

import com.migrationreport.entity.User;
import com.migrationreport.repository.UserRepository;
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
            // Hash the incoming password and compare to the DB stored hash
            String hashedInput = com.migrationreport.util.EncryptionUtil.hashPassword(password);
            return hashedInput.equals(userOpt.get().getPassword());
        }
        return false;
    }
}
