package com.siquaras.auditlog.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.siquaras.auditlog.service.ApiExceptionCaptureManager;
import com.siquaras.auditlog.service.ApiExceptionCaptureManager.Settings;
import com.siquaras.auditlog.dto.CapturedApiLogRecord;
import com.siquaras.auditlog.dto.CaptureRequest;
import com.siquaras.auditlog.dto.CaptureStatus;

/** Runtime controls for observing API exceptions and selected log levels. */
@RestController
@RequestMapping("/auditLog/exceptionCapture")
public class ApiExceptionCaptureController {

	private final ApiExceptionCaptureManager captureManager;

	public ApiExceptionCaptureController(ApiExceptionCaptureManager captureManager) {
		this.captureManager = captureManager;
	}

	@GetMapping
	public CaptureStatus status() {
		Settings settings = captureManager.getSettings();
		
		try {
			
			return toStatus(settings);
			
		} catch (IllegalStateException ex) {
			
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
		}
	}

	@PutMapping
	public CaptureStatus update(@RequestBody CaptureRequest request) {
		
		try {

			if (request == null || request.enabled() == null) {
				throw new IllegalArgumentException("enabled is required.");
			}
			
			Settings settings = request.enabled() ? captureManager.start(request.levels(), request.durationSeconds()) : captureManager.stop();
			
			return toStatus(settings);
		} catch (IllegalArgumentException ex) {
			
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
			
		} catch (IllegalStateException ex) {
			
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
		}
	}

	@GetMapping("/events")
	public List<CapturedApiLogRecord> events(@RequestParam(name = "limit", defaultValue = "100") int limit) {
		
		try {
			
			return captureManager.findRecent(limit);
			
		} catch (IllegalArgumentException ex) {
			
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
			
		} catch (IllegalStateException ex) {
			
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
		}
	}

	private CaptureStatus toStatus(Settings settings) {
		return new CaptureStatus(settings.enabled(), settings.levels(), captureManager.getExpiresAt(),
				captureManager.getStoredCount(), captureManager.getStoredBytes(), captureManager.getDroppedEvents(),
				captureManager.getWriteFailures(), captureManager.isLogCaptureAvailable(),
				captureManager.getLoggingBackend());
	}
}
