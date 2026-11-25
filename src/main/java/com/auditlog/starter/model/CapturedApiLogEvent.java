package com.auditlog.starter.model;

import java.time.Instant;

/** Data captured from an API log event before it is persisted to the temporary H2 database. */
public record CapturedApiLogEvent(
        Instant capturedAt,
        Instant loggedAt,
        String eventType,
        String level,
        String loggerName,
        String threadName,
        String message,
        String exceptionType,
        String exceptionMessage,
        String stackTrace,
        String requestMethod,
        String requestPath,
        String requestId,
        String handler,
        String sourceClass,
        String sourceMethod) {
    public CapturedApiLogEvent(Instant capturedAt, Instant loggedAt, String eventType, String level,
            String loggerName, String threadName, String message, String exceptionType, String exceptionMessage,
            String stackTrace, String requestMethod, String requestPath, String requestId, String handler) {
        this(capturedAt, loggedAt, eventType, level, loggerName, threadName, message, exceptionType,
                exceptionMessage, stackTrace, requestMethod, requestPath, requestId, handler, null, null);
    }
}
