package com.rido.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record RegisterRequest(
    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 30, message = "Username must be 3–30 characters")
    @jakarta.validation.constraints.Pattern(
        regexp = "^[a-zA-Z0-9._-]{3,30}$",
        message = "Username can only contain letters, numbers, dots, underscores, and hyphens"
    )
    String username,

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @jakarta.validation.constraints.Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,100}$",
        message = "Password must contain at least one uppercase, one lowercase, one digit, and one special character"
    )
    String password
) {}
