package com.auditlog.starter.model;

import java.time.Instant;

/** Spring event emitted for selected log levels while runtime capture is enabled. */
public record ApiLogCapturedEvent(
        Instant timestamp,
        String loggerName,
        String threadName,
        String level,
        String message,
        String sourceClass,
        String sourceMethod) {
    public ApiLogCapturedEvent(Instant timestamp, String loggerName, String threadName, String level, String message) {
        this(timestamp, loggerName, threadName, level, message, null, null);
    }
}
