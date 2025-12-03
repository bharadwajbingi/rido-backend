package com.rido.auth.dto;

import jakarta.validation.constraints.NotBlank;


public record LoginRequest(
    @NotBlank(message = "Username cannot be empty")
    String username,

    @NotBlank(message = "Password cannot be empty")
    String password,

    // OPTIONAL BUT NEEDED FOR SESSION TRACKING
    String deviceId,
    String ip,
    String userAgent
) {}
