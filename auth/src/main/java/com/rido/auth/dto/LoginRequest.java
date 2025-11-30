package com.rido.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    private String password;

    // OPTIONAL BUT NEEDED FOR SESSION TRACKING
    private String deviceId;
    private String ip;
    private String userAgent;
}
