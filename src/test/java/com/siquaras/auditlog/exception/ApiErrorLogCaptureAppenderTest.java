package com.siquaras.auditlog.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;

import com.siquaras.auditlog.model.ApiLogCapturedEvent;
import com.siquaras.auditlog.dto.CapturedApiLogRecord;
import com.siquaras.auditlog.service.ApiExceptionCaptureManager;
import com.siquaras.auditlog.logging.ApiErrorLogCaptureAppender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;

class ApiErrorLogCaptureAppenderTest {
    private final ApiExceptionCaptureManager manager = new ApiExceptionCaptureManager();
    private ApiErrorLogCaptureAppender appender;

    @AfterEach
    void cleanup() {
        if (appender != null) appender.stop();
        manager.close();
    }

    @Test
    void capturesSelectedLogAndPublishesSpringEventOffTheLoggingThread() throws Exception {
        manager.start(java.util.List.of("INFO"), 60L);
        AtomicReference<org.springframework.context.ApplicationEvent> published = new AtomicReference<>();
        ApplicationEventPublisher publisher = event -> published.set(
                event instanceof org.springframework.context.ApplicationEvent applicationEvent
                        ? applicationEvent : new PayloadApplicationEvent<>(this, event));
        appender = new ApiErrorLogCaptureAppender(manager, publisher);
        appender.start();

        LoggingEvent logEvent = new LoggingEvent();
        logEvent.setLevel(Level.INFO);
        logEvent.setLoggerName("consumer.Logger");
        logEvent.setThreadName("http-worker");
        logEvent.setMessage("{\"status\":\"ok\"}");
        logEvent.setTimeStamp(System.currentTimeMillis());
        logEvent.setCallerData(new StackTraceElement[] {
                new StackTraceElement("com.example.catalog.CatalogService", "findById", "CatalogService.java", 42) });
        logEvent.setMDCPropertyMap(Map.of("audit.request.path", "/test"));
        appender.doAppend(logEvent);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while ((manager.getStoredCount() == 0 || published.get() == null) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(1, manager.getStoredCount());
        assertEquals("/test", manager.findRecent(1).get(0).requestPath());
        assertEquals("com.example.catalog.CatalogService", manager.findRecent(1).get(0).sourceClass());
        assertEquals("findById", manager.findRecent(1).get(0).sourceMethod());
        assertEquals("ok", manager.findRecent(1).get(0).eventJson().get("status").asText());
        assertInstanceOf(PayloadApplicationEvent.class, published.get());
        PayloadApplicationEvent<?> payload = (PayloadApplicationEvent<?>) published.get();
        assertTrue(payload.getPayload() instanceof ApiLogCapturedEvent);
        ApiLogCapturedEvent capturedEvent = (ApiLogCapturedEvent) payload.getPayload();
        assertEquals("com.example.catalog.CatalogService", capturedEvent.sourceClass());
        assertEquals("findById", capturedEvent.sourceMethod());
    }

    @Test
    void capturesExceptionOriginInsteadOfTheAppenderOrResolver() throws Exception {
        manager.start(java.util.List.of("ERROR"), 60L);
        appender = createAppender(new AtomicReference<>());
        LoggingEvent logEvent = new LoggingEvent();
        logEvent.setLevel(Level.ERROR);
        logEvent.setLoggerName("com.siquaras.auditlog.exception.ApiExceptionLoggingResolver");
        logEvent.setThreadName("http-worker");
        logEvent.setMessage("API exception observed");
        IllegalStateException exception = new IllegalStateException("failed");
        exception.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.example.orders.OrderService", "cancelOrder", "OrderService.java", 77) });
        logEvent.setThrowableProxy(new ThrowableProxy(exception));
        logEvent.setMDCPropertyMap(Map.of());
        appender.doAppend(logEvent);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (manager.getStoredCount() == 0 && System.nanoTime() < deadline) Thread.sleep(10L);
        CapturedApiLogRecord record = manager.findRecent(1).get(0);
        assertEquals("EXCEPTION", record.eventType());
        assertEquals("com.example.orders.OrderService", record.sourceClass());
        assertEquals("cancelOrder", record.sourceMethod());
    }

    private ApiErrorLogCaptureAppender createAppender(AtomicReference<org.springframework.context.ApplicationEvent> published) {
        ApplicationEventPublisher publisher = event -> published.set(
                event instanceof org.springframework.context.ApplicationEvent applicationEvent
                        ? applicationEvent : new PayloadApplicationEvent<>(this, event));
        ApiErrorLogCaptureAppender created = new ApiErrorLogCaptureAppender(manager, publisher);
        created.start();
        return created;
    }
}
