package com.rido.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/secure")
public class SecureController {

    @GetMapping("/info")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> secureInfo(Authentication auth) {
        return ResponseEntity.ok(Map.of(
            "userId", auth.getName(),
            "roles", auth.getAuthorities()
        ));
    }
}
