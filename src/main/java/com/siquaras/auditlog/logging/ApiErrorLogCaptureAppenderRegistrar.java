package com.siquaras.auditlog.logging;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import com.siquaras.auditlog.service.ApiExceptionCaptureManager;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/** Attaches the observer to Logback's root logger and removes it at shutdown. */
public class ApiErrorLogCaptureAppenderRegistrar implements SmartInitializingSingleton, DisposableBean {

    private final ApiExceptionCaptureManager captureManager;
    private final ApplicationEventPublisher eventPublisher;

    private Logger rootLogger;
    private ApiErrorLogCaptureAppender appender;

    public ApiErrorLogCaptureAppenderRegistrar(ApiExceptionCaptureManager captureManager, ApplicationEventPublisher eventPublisher) {
        this.captureManager = captureManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        
        if (!(loggerFactory instanceof LoggerContext loggerContext)) {
            return;
        }

        rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ApiErrorLogCaptureAppender(captureManager, eventPublisher);
        appender.setContext(loggerContext);
        appender.setName("AUDIT_LOG_API_ERROR_CAPTURE");
        appender.start();
        rootLogger.addAppender(appender);
        captureManager.setLoggingBackend("Logback");
    }

    @Override
    public void destroy() {
        
    	if (rootLogger != null && appender != null) {
            rootLogger.detachAppender(appender);
            appender.stop();
            captureManager.setLoggingBackend("unavailable");
        }
    }
}
