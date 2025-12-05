package com.rido.auth.dto;

import jakarta.validation.constraints.NotBlank;


public record LoginRequest(
    @NotBlank(message = "Username cannot be empty")
    @jakarta.validation.constraints.Pattern(
        regexp = "^[a-zA-Z0-9._-]{3,30}$",
        message = "Invalid username format"
    )
    String username,

    @NotBlank(message = "Password cannot be empty")
    @jakarta.validation.constraints.Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,100}$",
        message = "Invalid password format"
    )
    String password,

    // OPTIONAL BUT NEEDED FOR SESSION TRACKING
    String deviceId,
    String ip,
    String userAgent
) {}
