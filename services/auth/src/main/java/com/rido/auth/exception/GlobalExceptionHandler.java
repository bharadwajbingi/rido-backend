package com.rido.auth.exception;

import com.rido.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private ErrorResponse buildErrorResponse(HttpStatus status, String error, String message, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        return new ErrorResponse(status.value(), error, message, path);
    }

    // ============================================
    // VALIDATION ERRORS
    // ============================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldError() != null 
                ? e.getBindingResult().getFieldError().getDefaultMessage() 
                : "Validation failed";
        // DEBUG LOG
        System.out.println("VALIDATION FAILED: " + msg);
        e.getBindingResult().getAllErrors().forEach(err -> System.out.println("Error: " + err));
        
        ErrorResponse error = buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error", msg, request);
        return ResponseEntity.badRequest().body(error);
    }

    // ============================================
    // AUTH + LOGIN EXCEPTIONS
    // ============================================
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCreds(InvalidCredentialsException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(AccountLockedException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.LOCKED, "Account Locked", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.LOCKED).body(error);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameExists(UsernameAlreadyExistsException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.CONFLICT, "Conflict", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ============================================
    // REFRESH & TOKEN SECURITY
    // ============================================
    @ExceptionHandler(ReplayDetectedException.class)
    public ResponseEntity<ErrorResponse> handleReplay(ReplayDetectedException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.UNAUTHORIZED, "Replay Detected", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(TokenExpiredException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.UNAUTHORIZED, "Token Expired", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(DeviceMismatchException.class)
    public ResponseEntity<ErrorResponse> handleDeviceMismatch(DeviceMismatchException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.UNAUTHORIZED, "Device Mismatch", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ============================================
    // RATE LIMITING
    // ============================================
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(TooManyRequestsException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    // ============================================
    // MALFORMED JSON
    // ============================================
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or malformed JSON", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ============================================
    // ACCESS DENIED
    // ============================================
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        ErrorResponse error = buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", "Access Denied", request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ============================================
    // FALLBACK
    // ============================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e, HttpServletRequest request) {
        System.out.println("GENERIC EXCEPTION CAUGHT: " + e.getClass().getName() + " - " + e.getMessage());
        e.printStackTrace(); // Log the full stack trace
        ErrorResponse error = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Internal Server Error", 
                "Internal error: " + e.getMessage(), 
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
