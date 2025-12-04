package com.rido.auth.model;

/**
 * Enum representing different types of audit events for security-sensitive actions.
 * Used to categorize and track authentication and authorization activities.
 */
public enum AuditEvent {
    /**
     * User successfully logged in
     */
    LOGIN_SUCCESS,

    /**
     * Login attempt failed (invalid credentials, account locked, etc.)
     */
    LOGIN_FAILED,

    /**
     * User logged out
     */
    LOGOUT,

    /**
     * Refresh token was used to obtain new access token
     */
    REFRESH_TOKEN,

    /**
     * New user account was created
     */
    SIGNUP,

    /**
     * JWT signing keys were rotated
     */
    KEY_ROTATION,

    /**
     * New admin account was created
     */
    ADMIN_CREATION,

    /**
     * Refresh token rejected due to device or user agent mismatch
     */
    DEVICE_MISMATCH,

    /**
     * User profile was updated
     */
    PROFILE_UPDATED
}
