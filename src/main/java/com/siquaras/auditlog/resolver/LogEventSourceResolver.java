package com.siquaras.auditlog.resolver;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import java.util.Map;

import com.siquaras.auditlog.logging.ApiErrorLogCaptureAppender;
import com.siquaras.auditlog.service.ApiExceptionCaptureManager;

/** Resolves the application class and method that produced a log event. */
public final class LogEventSourceResolver {
    private LogEventSourceResolver() { }

    public static Source resolve(ILoggingEvent event) {
        Source source = throwableSource(event.getThrowableProxy());
        if (source == null) source = mdcSource(event.getMDCPropertyMap());
        if (source == null) source = callerDataSource(event);
        if (source == null) source = currentStackSource();
        if (source != null) return source;
        String loggerName = event.getLoggerName();
        return new Source(loggerName == null || loggerName.isBlank() ? "UNKNOWN" : loggerName, "UNKNOWN");
    }

    private static Source throwableSource(IThrowableProxy throwable) {
        if (throwable == null) return null;
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        if (frames == null) return null;
        for (StackTraceElementProxy frame : frames) {
            if (frame == null || frame.getStackTraceElement() == null) continue;
            StackTraceElement element = frame.getStackTraceElement();
            if (isThrowableApplicationFrame(element.getClassName())) return source(element);
        }
        return null;
    }

    private static Source callerDataSource(ILoggingEvent event) {
        try {
            StackTraceElement[] frames = event.getCallerData();
            if (frames == null) return null;
            for (StackTraceElement frame : frames) {
                if (frame != null && isCallerFrame(frame.getClassName())) return source(frame);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the current stack if the backend has no caller data.
        }
        return null;
    }

    private static Source currentStackSource() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame != null && isCallerFrame(frame.getClassName())) return source(frame);
        }
        return null;
    }

    private static Source mdcSource(Map<String, String> mdc) {
        if (mdc == null) return null;
        String className = mdc.get("audit.source.class");
        String methodName = mdc.get("audit.source.method");
        return className == null || methodName == null ? null : new Source(className, methodName);
    }

    private static boolean isCallerFrame(String className) {
        return className != null
                && !className.startsWith("java.")
                && !className.startsWith("jdk.")
                && !className.startsWith("sun.")
                && !className.startsWith("ch.qos.logback.")
                && !className.startsWith("org.slf4j.")
                && !className.equals(LogEventSourceResolver.class.getName())
                && !className.equals(ApiErrorLogCaptureAppender.class.getName())
                && !className.equals(ApiExceptionLoggingResolver.class.getName())
                && !className.equals(ApiExceptionCaptureManager.class.getName())
                && !className.equals("com.siquaras.auditlog.repository.TemporaryH2CaptureStore");
    }

    private static boolean isThrowableApplicationFrame(String className) {
        return isCallerFrame(className)
                && !className.startsWith("org.springframework.")
                && !className.startsWith("org.apache.")
                && !className.startsWith("jakarta.")
                && !className.startsWith("javax.");
    }

    private static Source source(StackTraceElement frame) {
        String method = frame.getMethodName();
        return new Source(frame.getClassName(), method == null || method.isBlank() ? "UNKNOWN" : method);
    }

    public record Source(String className, String methodName) { }
}
