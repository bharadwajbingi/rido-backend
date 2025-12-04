package com.rido.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/secure")
public class SecureController {

    @GetMapping("/info")
    public ResponseEntity<?> secureInfo(HttpServletRequest req) {

        // --------------------------------------------------
        // 1) Read userId set by JwtUserAuthenticationFilter
        // --------------------------------------------------
        String userId = (String) req.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized"));
        }

        // --------------------------------------------------
        // 2) Extract roles safely (JWT stores as List<Object>)
        // --------------------------------------------------
        Object rolesObj = req.getAttribute("roles");

        List<String> roles = null;

        if (rolesObj instanceof List<?>) {
            roles = ((List<?>) rolesObj)
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else if (rolesObj instanceof String) {
            // Single role case
            roles = List.of((String) rolesObj);
        } else {
            roles = List.of();
        }

        // --------------------------------------------------
        // 3) Response — fully safe, well-structured
        // --------------------------------------------------
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "roles", roles
        ));
    }
}
