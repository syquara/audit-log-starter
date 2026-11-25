package com.siquaras.auditlog.logging;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;

import com.siquaras.auditlog.model.ApiLogCapturedEvent;
import com.siquaras.auditlog.model.CapturedApiLogEvent;
import com.siquaras.auditlog.service.ApiExceptionCaptureManager;
import com.siquaras.auditlog.resolver.LogEventSourceResolver;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

/** Observes selected Logback events and stores them asynchronously while capture is enabled. */
public class ApiErrorLogCaptureAppender extends AppenderBase<ILoggingEvent> {
    
	private final ApiExceptionCaptureManager captureManager;
    private final ApplicationEventPublisher eventPublisher;

    public ApiErrorLogCaptureAppender(ApiExceptionCaptureManager captureManager, ApplicationEventPublisher eventPublisher) {
        this.captureManager = captureManager;
        this.eventPublisher = eventPublisher;
    }

    @Override protected void append(ILoggingEvent event) {
    	
        if (!captureManager.shouldCaptureLogLevel(event.getLevel().toString())) {
        	return;
        }
        
        try {
            
        	IThrowableProxy throwable = event.getThrowableProxy();
            var mdc = event.getMDCPropertyMap();
            
            Instant loggedAt = Instant.ofEpochMilli(event.getTimeStamp());
            String message = event.getFormattedMessage();
            String loggerName = event.getLoggerName();
            String threadName = event.getThreadName();
            String level = event.getLevel().toString();
            
            LogEventSourceResolver.Source source = LogEventSourceResolver.resolve(event);
            
            captureManager.capture(new CapturedApiLogEvent(Instant.now(), loggedAt,
                    throwable == null ? "LOG" : "EXCEPTION", event.getLevel().toString(), event.getLoggerName(), event.getThreadName(),
                    event.getFormattedMessage(), throwable == null ? null : throwable.getClassName(),
                    throwable == null ? null : throwable.getMessage(), throwable == null ? null : ThrowableProxyUtil.asString(throwable),
                    mdc.get("audit.request.method"), mdc.get("audit.request.path"), mdc.get("audit.request.id"),
                    mdc.get("audit.request.handler"), source.className(), source.methodName()),
                    () -> eventPublisher.publishEvent(new ApiLogCapturedEvent(
                            loggedAt, loggerName, threadName, level, message, source.className(), source.methodName())));
            
        } catch (RuntimeException ignored) { 
        	/* Capture must never affect the consumer's logging call. */ 
        }
    }
}
