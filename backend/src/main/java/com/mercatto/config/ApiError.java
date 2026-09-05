package com.mercatto.config;

import java.time.Instant;

/**
 * Standardized error response body returned by {@link GlobalExceptionHandler}.
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {}
