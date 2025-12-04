package com.rido.auth.debug;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Profile({"dev", "test"})   // ACTIVE ONLY IN DEV + TEST
@RestController
@RequestMapping("/auth/debug")
public class DebugController {

    private final TestLoginResetService resetService;

    public DebugController(TestLoginResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        resetService.resetFailures(username);
        return ResponseEntity.ok(Map.of("status", "unlocked"));
    }
}
