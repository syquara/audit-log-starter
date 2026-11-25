package com.auditlog.starter.dto;

import java.time.Instant;
import java.util.List;

/** Current state and resource counters for a temporary capture session. */
public record CaptureStatus(boolean enabled, 
							List<String> levels, 
							Instant expiresAt, 
							long storedCount, 
							long storedBytes,
							long droppedEvents, 
							long writeFailures, 
							boolean logCaptureAvailable, 
							String loggingBackend) {
	
	public CaptureStatus(boolean enabled, List<String> levels, Instant expiresAt, long storedCount, long droppedEvents, long writeFailures) {
		
		this(enabled, levels, expiresAt, storedCount, 0, droppedEvents, writeFailures, false, "unavailable");
	}
}
