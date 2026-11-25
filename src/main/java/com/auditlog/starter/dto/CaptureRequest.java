package com.auditlog.starter.dto;

import java.util.List;

/** Request payload for runtime capture controls. */
public record CaptureRequest(Boolean enabled, List<String> levels, Long durationSeconds) {

	public CaptureRequest(Boolean enabled, List<String> levels) {
		this(enabled, levels, null);
	}
}
