package com.auditlog.starter.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/** Row exposed by the temporary capture query endpoint. */
public record CapturedApiLogRecord(
        long id,
        Instant capturedAt,
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
        String sourceMethod,
        JsonNode eventJson) {
	
    public CapturedApiLogRecord(long id, Instant capturedAt, String eventType, String level, String loggerName,
            String threadName, String message, String exceptionType, String exceptionMessage, String stackTrace,
            String requestMethod, String requestPath, String requestId, String handler, JsonNode eventJson) {
     
    	this(id, capturedAt, eventType, level, loggerName, threadName, message, exceptionType, exceptionMessage,
                stackTrace, requestMethod, requestPath, requestId, handler, null, null, eventJson);
    }
}
