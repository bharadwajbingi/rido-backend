package com.rido.auth.exception;

import com.rido.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/auth/test");
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrors {

        @Test
        @DisplayName("Should return 400 for validation errors")
        void shouldReturn400_forValidationErrors() {
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError fieldError = new FieldError("object", "field", "must not be blank");
            when(bindingResult.getFieldError()).thenReturn(fieldError);
            
            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            when(exception.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidation(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Authentication Error Tests")
    class AuthenticationErrors {

        @Test
        @DisplayName("Should return 401 for invalid credentials")
        void shouldReturn401_forInvalidCredentials() {
            InvalidCredentialsException exception = new InvalidCredentialsException("Invalid username or password");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidCreds(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(401);
            assertThat(response.getBody().getError()).isEqualTo("Unauthorized");
        }

        @Test
        @DisplayName("Should return 423 for locked account")
        void shouldReturn423_forLockedAccount() {
            AccountLockedException exception = new AccountLockedException("Account locked");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleLocked(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(423);
        }

        @Test
        @DisplayName("Should return 409 for duplicate username")
        void shouldReturn409_forDuplicateUsername() {
            UsernameAlreadyExistsException exception = new UsernameAlreadyExistsException("Username already exists");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleUsernameExists(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(409);
        }
    }

    @Nested
    @DisplayName("Token Error Tests")
    class TokenErrors {

        @Test
        @DisplayName("Should return 401 for replay detected")
        void shouldReturn401_forReplayDetected() {
            ReplayDetectedException exception = new ReplayDetectedException("Token replay detected");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleReplay(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError()).isEqualTo("Replay Detected");
        }

        @Test
        @DisplayName("Should return 401 for expired token")
        void shouldReturn401_forExpiredToken() {
            TokenExpiredException exception = new TokenExpiredException("Token expired");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleExpired(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError()).isEqualTo("Token Expired");
        }

        @Test
        @DisplayName("Should return 401 for device mismatch")
        void shouldReturn401_forDeviceMismatch() {
            DeviceMismatchException exception = new DeviceMismatchException("Device mismatch");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleDeviceMismatch(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getError()).isEqualTo("Device Mismatch");
        }
    }

    @Nested
    @DisplayName("Rate Limit Error Tests")
    class RateLimitErrors {

        @Test
        @DisplayName("Should return 429 for rate limit exceeded")
        void shouldReturn429_forRateLimitExceeded() {
            TooManyRequestsException exception = new TooManyRequestsException("Too many requests");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleRateLimit(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(429);
        }
    }

    @Nested
    @DisplayName("Bad Request Error Tests")
    class BadRequestErrors {

        @Test
        @DisplayName("Should return 400 for malformed JSON")
        void shouldReturn400_forMalformedJson() {
            HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadJson(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid or malformed JSON");
        }
    }

    @Nested
    @DisplayName("Authorization Error Tests")
    class AuthorizationErrors {

        @Test
        @DisplayName("Should return 403 for access denied")
        void shouldReturn403_forAccessDenied() {
            AccessDeniedException exception = new AccessDeniedException("Access denied");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Generic Error Tests")
    class GenericErrors {

        @Test
        @DisplayName("Should return 500 for generic exception")
        void shouldReturn500_forGenericException() {
            Exception exception = new RuntimeException("Unexpected error");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneric(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(500);
        }
    }
}
