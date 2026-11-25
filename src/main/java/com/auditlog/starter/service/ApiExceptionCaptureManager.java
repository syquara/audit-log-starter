package com.auditlog.starter.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.auditlog.starter.model.CapturedApiLogEvent;
import com.auditlog.starter.dto.CapturedApiLogRecord;
import com.auditlog.starter.repository.TemporaryH2CaptureStore;

/** Coordinates runtime capture settings, asynchronous writes, and capture-session lifecycle. */
public class ApiExceptionCaptureManager implements AutoCloseable {
    public static final long DEFAULT_DURATION_SECONDS = 120;
    public static final long MAX_DURATION_SECONDS = 3600;
    public static final int MAX_RECORDS = 10000;
    public static final long MAX_TOTAL_STORAGE_BYTES = 32L * 1024 * 1024;
    public static final int MAX_QUEUE_SIZE = 256;
    public static final int MAX_MESSAGE_BYTES = 32768;
    public static final int MAX_EXCEPTION_MESSAGE_BYTES = 8192;
    public static final int MAX_STACK_TRACE_BYTES = 65536;
    private static final Set<String> SUPPORTED_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "ALL");

    private final AtomicReference<Settings> settings = new AtomicReference<>(new Settings(false, List.of()));
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "audit-log-h2-timer"));
    private volatile TemporaryH2CaptureStore store;
    private volatile ThreadPoolExecutor writer;
    private volatile ScheduledFuture<?> expiration;
    private volatile Instant expiresAt;
    private volatile String loggingBackend = "unavailable";

    public synchronized Settings start(List<String> levels, Long durationSeconds) {
        long duration = durationSeconds == null ? DEFAULT_DURATION_SECONDS : durationSeconds;
        if (duration < 1 || duration > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("durationSeconds must be between 1 and " + MAX_DURATION_SECONDS + ".");
        }
        List<String> normalized = levels == null ? List.of() : normalize(levels);
        if (normalized.isEmpty()) throw new IllegalArgumentException("At least one log level is required.");
        stop();
        TemporaryH2CaptureStore newStore = new TemporaryH2CaptureStore();
        ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
        ThreadPoolExecutor newWriter = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue,
                r -> daemon(r, "audit-log-h2-writer"), new ThreadPoolExecutor.AbortPolicy());
        store = newStore;
        writer = newWriter;
        droppedEvents.set(0);
        writeFailures.set(0);
        expiresAt = Instant.now().plusSeconds(duration);
        settings.set(new Settings(true, normalized));
        expiration = timer.schedule(this::stop, duration, TimeUnit.SECONDS);
        return settings.get();
    }

    public synchronized Settings stop() {
        settings.set(new Settings(false, List.of()));
        if (expiration != null) { expiration.cancel(false); expiration = null; }
        expiresAt = null;
        ThreadPoolExecutor currentWriter = writer;
        writer = null;
        if (currentWriter != null) {
            currentWriter.shutdown();
            try {
                if (!currentWriter.awaitTermination(3, TimeUnit.SECONDS)) {
                    droppedEvents.addAndGet(currentWriter.shutdownNow().size());
                }
            } catch (InterruptedException ex) {
                droppedEvents.addAndGet(currentWriter.shutdownNow().size());
                Thread.currentThread().interrupt();
            }
        }
        TemporaryH2CaptureStore currentStore = store;
        store = null;
        if (currentStore != null) currentStore.close();
        return settings.get();
    }

    public Settings getSettings() { return settings.get(); }
    /** @deprecated Use {@link #start(List, Long)} and {@link #stop()} so the store lifecycle is controlled too. */
    @Deprecated
    public Settings update(Boolean enabled, List<String> levels) {
        if (enabled == null) return getSettings();
        return enabled ? start(levels, null) : stop();
    }
    public boolean isEnabled() { return settings.get().enabled(); }
    public Instant getExpiresAt() { return expiresAt; }
    public long getDroppedEvents() { return droppedEvents.get(); }
    public long getWriteFailures() { return writeFailures.get(); }
    public String getLoggingBackend() { return loggingBackend; }
    public boolean isLogCaptureAvailable() { return !"unavailable".equals(loggingBackend); }
    public void setLoggingBackend(String backend) { loggingBackend = backend == null ? "unavailable" : backend; }

    public boolean shouldCaptureLogLevel(String level) {
        if (level == null) return false;
        Settings current = settings.get();
        String normalized = level.toUpperCase(Locale.ROOT);
        return current.enabled() && (current.levels().contains("ALL") || current.levels().contains(normalized));
    }

    public void capture(CapturedApiLogEvent event) {
        capture(event, null);
    }

    public void capture(CapturedApiLogEvent event, Runnable observer) {
        ThreadPoolExecutor current = writer;
        if (!isEnabled() || current == null) return;
        CapturedApiLogEvent bounded = bound(event);
        try {
            current.execute(() -> {
                insert(bounded);
                if (observer != null) {
                    try { observer.run(); }
                    catch (RuntimeException ignored) { /* Observer failures must not affect application logging. */ }
                }
            });
        }
        catch (RejectedExecutionException ex) { droppedEvents.incrementAndGet(); }
    }

    public List<CapturedApiLogRecord> findRecent(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000.");
        TemporaryH2CaptureStore current = store;
        return !isEnabled() || current == null ? List.of() : current.findRecent(limit);
    }

    public long getStoredCount() {
        TemporaryH2CaptureStore current = store;
        return !isEnabled() || current == null ? 0 : current.count();
    }

    public long getStoredBytes() {
        TemporaryH2CaptureStore current = store;
        return !isEnabled() || current == null ? 0 : current.storedBytes();
    }

    private void insert(CapturedApiLogEvent event) {
        TemporaryH2CaptureStore current = store;
        if (current == null) return;
        try {
            if (!current.insert(event)) droppedEvents.incrementAndGet();
        }
        catch (RuntimeException ex) { writeFailures.incrementAndGet(); }
    }

    private static CapturedApiLogEvent bound(CapturedApiLogEvent event) {
        String sourceClass = event.sourceClass() == null || event.sourceClass().isBlank()
                ? (event.loggerName() == null || event.loggerName().isBlank() ? "UNKNOWN" : event.loggerName())
                : event.sourceClass();
        String sourceMethod = event.sourceMethod() == null || event.sourceMethod().isBlank()
                ? "UNKNOWN" : event.sourceMethod();
        return new CapturedApiLogEvent(event.capturedAt(), event.loggedAt(), truncate(event.eventType(), 80),
                truncate(event.level(), 16), truncate(event.loggerName(), 512), truncate(event.threadName(), 256),
                truncateUtf8(event.message(), MAX_MESSAGE_BYTES), truncate(event.exceptionType(), 512),
                truncateUtf8(event.exceptionMessage(), MAX_EXCEPTION_MESSAGE_BYTES),
                truncateUtf8(event.stackTrace(), MAX_STACK_TRACE_BYTES), truncate(event.requestMethod(), 32),
                truncate(event.requestPath(), 2048), truncate(event.requestId(), 256), truncate(event.handler(), 1024),
                truncate(sourceClass, 512), truncate(sourceMethod, 512));
    }

    private static String truncate(String value, int maxCharacters) {
        return value == null || value.length() <= maxCharacters ? value : value.substring(0, maxCharacters);
    }

    private static String truncateUtf8(String value, int maxBytes) {
        if (value == null) return null;
        StringBuilder bounded = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int codePointBytes = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (bytes + codePointBytes > maxBytes) return bounded.toString();
            bounded.appendCodePoint(codePoint);
            bytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    private static List<String> normalize(List<String> levels) {
        if (levels.size() > SUPPORTED_LEVELS.size()) throw new IllegalArgumentException("Unsupported log levels. Use TRACE, DEBUG, INFO, WARN, ERROR or ALL.");
        List<String> normalized = new ArrayList<>();
        for (String level : levels) {
            if (level == null || level.isBlank()) throw new IllegalArgumentException("Log levels cannot be blank.");
            String value = level.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_LEVELS.contains(value)) throw new IllegalArgumentException("Unsupported log level: " + level);
            if (!normalized.contains(value)) normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private static Thread daemon(Runnable task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }
    @Override public void close() { stop(); timer.shutdownNow(); }
    public record Settings(boolean enabled, List<String> levels) { public Settings { levels = List.copyOf(levels); } }
}
