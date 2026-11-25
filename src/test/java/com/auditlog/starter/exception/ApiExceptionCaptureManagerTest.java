package com.auditlog.starter.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.auditlog.starter.model.CapturedApiLogEvent;
import com.auditlog.starter.dto.CapturedApiLogRecord;
import com.auditlog.starter.service.ApiExceptionCaptureManager;

class ApiExceptionCaptureManagerTest {
    private final ApiExceptionCaptureManager manager = new ApiExceptionCaptureManager();

    @AfterEach
    void closeManager() { manager.close(); }

    @Test
    void startsCaptureAndStoresLogAndExceptionMetadataAndJson() throws Exception {
        manager.start(List.of("info", "ERROR"), 60L);
        String json = "{\"message\":\"captured\"}";
        manager.capture(event("LOG", "INFO", json, null, null));
        awaitStoredCount(1);

        CapturedApiLogRecord record = manager.findRecent(10).get(0);
        assertEquals("LOG", record.eventType());
        assertEquals("INFO", record.level());
        assertEquals("logger", record.loggerName());
        assertEquals(json, record.message());
        assertEquals("captured", record.eventJson().get("message").asText());
        assertNotNull(record.capturedAt());
        assertTrue(manager.getStoredBytes() > 0);
        assertTrue(manager.getStoredBytes() <= ApiExceptionCaptureManager.MAX_TOTAL_STORAGE_BYTES);
    }

    @Test
    void appliesPerFieldUtf8LimitsAndPreservesUnicodeBoundaries() throws Exception {
        manager.start(List.of("ALL"), 60L);
        String largeMessage = "🚀".repeat(20000);
        manager.capture(event("LOG", "INFO", largeMessage, null, null));
        awaitStoredCount(1);

        String stored = manager.findRecent(1).get(0).message();
        assertTrue(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= ApiExceptionCaptureManager.MAX_MESSAGE_BYTES);
        assertFalse(stored.endsWith("\uD83D"));
    }

    @Test
    void stopsAndDestroysTheSessionAndValidatesLimits() {
        assertThrows(IllegalArgumentException.class, () -> manager.start(List.of(), 60L));
        assertThrows(IllegalArgumentException.class, () -> manager.start(List.of("INFO"), 3601L));

        manager.start(List.of("INFO"), 60L);
        assertTrue(manager.shouldCaptureLogLevel("info"));
        assertFalse(manager.shouldCaptureLogLevel("DEBUG"));
        assertEquals(0, manager.findRecent(1).size());
        assertThrows(IllegalArgumentException.class, () -> manager.findRecent(1001));

        manager.stop();
        assertFalse(manager.isEnabled());
        assertEquals(0, manager.findRecent(1).size());
        assertEquals(0, manager.getStoredBytes());
    }

    @Test
    void expiresSessionAutomatically() throws InterruptedException {
        manager.start(List.of("ALL"), 1L);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (manager.isEnabled() && System.nanoTime() < deadline) Thread.sleep(20L);

        assertFalse(manager.isEnabled());
        assertEquals(0, manager.getStoredCount());
    }

    @Test
    void rejectsEventsWhenTheBoundedQueueIsFull() throws InterruptedException {
        manager.start(List.of("ALL"), 60L);
        CountDownLatch observerStarted = new CountDownLatch(1);
        CountDownLatch releaseObserver = new CountDownLatch(1);
        manager.capture(event("LOG", "INFO", "first", null, null), () -> {
            observerStarted.countDown();
            try { releaseObserver.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        assertTrue(observerStarted.await(3, TimeUnit.SECONDS));

        for (int i = 0; i < ApiExceptionCaptureManager.MAX_QUEUE_SIZE + 20; i++) {
            manager.capture(event("LOG", "INFO", "queued-" + i, null, null));
        }
        assertTrue(manager.getDroppedEvents() > 0);
        releaseObserver.countDown();
    }

    private void awaitStoredCount(long expected) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (manager.getStoredCount() < expected && System.nanoTime() < deadline) Thread.sleep(10L);
        assertEquals(expected, manager.getStoredCount());
    }

    private static CapturedApiLogEvent event(String type, String level, String message,
            String exceptionMessage, String stackTrace) {
        return new CapturedApiLogEvent(Instant.now(), Instant.now(), type, level, "logger", "test-thread", message,
                exceptionMessage == null ? null : "java.lang.IllegalStateException", exceptionMessage, stackTrace,
                "GET", "/test", "request-1", "TestController#test");
    }
}
