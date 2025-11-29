package com.rido.auth.controller;
import com.rido.auth.exception.AccountLockedException;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.dto.LoginRequest;
import com.rido.auth.dto.LogoutRequest;
import com.rido.auth.dto.RefreshRequest;
import com.rido.auth.dto.RegisterRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.exception.UsernameAlreadyExistsException;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final String jwtSecret;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            JwtConfig jwtConfig
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.jwtSecret = jwtConfig.secret();
    }

    // =========================
    // REGISTER
    // =========================
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            authService.register(req.getUsername(), req.getPassword());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (UsernameAlreadyExistsException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Registration failed"));
        }
    }

    // =========================
    // LOGIN
    // =========================
    // @PostMapping("/login")
    // public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
    //     try {
    //         String ipAddress = request.getRemoteAddr();
    //         String deviceId = request.getHeader("X-Device-Id");

    //         TokenResponse resp = authService.login(
    //                 req.getUsername(),
    //                 req.getPassword(),
    //                 deviceId,
    //                 ipAddress
    //         );

    //         return ResponseEntity.ok(resp);
    //     } catch (InvalidCredentialsException ex) {
    //         return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
    //                 .body(Map.of("error", "Invalid credentials"));
    //     } catch (Exception ex) {
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    //                 .body(Map.of("error", "Login failed"));
    //     }
    // }
    @PostMapping("/login")
public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
    try {
        String ipAddress = request.getRemoteAddr();
        String deviceId = request.getHeader("X-Device-Id");

        TokenResponse resp = authService.login(
                req.getUsername(),
                req.getPassword(),
                deviceId,
                ipAddress
        );

        return ResponseEntity.ok(resp);

    } catch (AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED) // 423
                .body(Map.of("error", ex.getMessage()));

    } catch (InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials"));

    } catch (Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed"));
    }
}


    // =========================
    // REFRESH TOKEN
    // =========================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest body, HttpServletRequest request) {

        String refresh = body.refreshToken();
        if (refresh == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing refreshToken"));
        }

        try {
            String ipAddress = request.getRemoteAddr();
            String deviceId = request.getHeader("X-Device-Id");

            TokenResponse resp = authService.refresh(refresh, deviceId, ipAddress);
            return ResponseEntity.ok(resp);

        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Refresh failed"));
        }
    }

    // =========================
    // LOGOUT
    // =========================
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutRequest body) {

        String refresh = body.refreshToken();
        if (refresh == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing refreshToken"));
        }

        try {
            authService.logout(refresh);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Logout failed"));
        }
    }

    // =========================
    // GET CURRENT USER
    // =========================
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(name = "Authorization", required = false) String auth) {
        try {
            if (auth == null || !auth.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "missing token"));
            }

            String token = auth.substring(7);

            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            var key = Keys.hmacShaKeyFor(keyBytes);

            var claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            UUID userId = UUID.fromString(claims.getSubject());

            return userRepository.findById(userId)
                    .map(u -> ResponseEntity.ok(Map.of(
                            "id", u.getId().toString(),
                            "username", u.getUsername()
                    )))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "user not found")));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid token"));
        }
    }

    // =========================
    // CHECK USERNAME
    // =========================
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        boolean exists = userRepository.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("available", !exists));
    }
}
