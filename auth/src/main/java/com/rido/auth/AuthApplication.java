package com.rido.auth;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.model.UserEntity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@SpringBootApplication
@EnableConfigurationProperties(JwtConfig.class)
@EnableJpaRepositories(basePackages = "com.rido.auth.repo")
@EntityScan(basePackages = "com.rido.auth.model")
@EnableScheduling
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    // ------------------------------------------------------------------
    // Automatically create FIRST admin user when DB is empty
    // ------------------------------------------------------------------
    // @Bean
    // public ApplicationRunner firstAdminBootstrap(
    //         UserRepository userRepository,
    //         PasswordEncoder passwordEncoder,   // <-- provided by SecurityConfig
    //         Environment env
    // ) {
    //     return args -> {
    //         long count = userRepository.count();

    //         if (count == 0) {
    //             String adminUser = env.getProperty("FIRST_ADMIN_USERNAME", "admin");
    //             String adminPass = env.getProperty("FIRST_ADMIN_PASSWORD");

    //             if (adminPass == null || adminPass.isBlank()) {
    //                 System.out.println("FIRST_ADMIN_PASSWORD not set — skipping first admin creation.");
    //                 return;
    //             }

    //             UserEntity admin = new UserEntity();
    //             admin.setUsername(adminUser);
    //             admin.setPasswordHash(passwordEncoder.encode(adminPass));
    //             admin.setRole("ADMIN");
    //             admin.setCreatedAt(Instant.now());

    //             userRepository.save(admin);

    //             System.out.println("✔ Created FIRST ADMIN account: " + adminUser);
    //         }
    //     };
    // }
}
