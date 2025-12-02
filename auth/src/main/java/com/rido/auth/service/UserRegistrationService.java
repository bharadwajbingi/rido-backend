package com.rido.auth.service;

import com.rido.auth.exception.UsernameAlreadyExistsException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(String username, String password) {
        log.info("auth_register_attempt", kv("username", username));

        userRepository.findByUsername(username)
                .ifPresent(u -> {
                    log.warn("auth_register_failed",
                            kv("username", username),
                            kv("reason", "username_taken"));
                    throw new UsernameAlreadyExistsException("Username already exists");
                });

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");

        userRepository.save(user);

        log.info("auth_register_success", kv("username", username));
    }
}
