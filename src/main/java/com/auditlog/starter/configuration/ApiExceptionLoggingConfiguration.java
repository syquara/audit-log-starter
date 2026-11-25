package com.auditlog.starter.configuration;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.auditlog.starter.controller.ApiExceptionCaptureController;
import com.auditlog.starter.logging.ApiErrorLogCaptureAppenderRegistrar;
import com.auditlog.starter.service.ApiExceptionCaptureManager;
import com.auditlog.starter.resolver.ApiExceptionLoggingResolver;

/**
 * Auto-configuration for runtime-controlled passive API exception logging.
 *
 * <p>Capture is disabled by default. The consuming API can enable or disable it
 * at runtime through {@code /auditLog/exceptionCapture}. The endpoint should
 * be protected by the consuming application's security configuration.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ApiExceptionLoggingConfiguration.LogbackCaptureConfiguration.class)
public class ApiExceptionLoggingConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiExceptionCaptureManager apiExceptionCaptureManager() {
        return new ApiExceptionCaptureManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiExceptionLoggingResolver apiExceptionLoggingResolver(ApiExceptionCaptureManager captureManager) {
        return new ApiExceptionLoggingResolver(captureManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiExceptionCaptureController apiExceptionCaptureController(ApiExceptionCaptureManager captureManager) {
        return new ApiExceptionCaptureController(captureManager);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
    static class LogbackCaptureConfiguration {

	    @Bean
	    @ConditionalOnMissingBean
	    ApiErrorLogCaptureAppenderRegistrar apiErrorLogCaptureAppenderRegistrar(ApiExceptionCaptureManager captureManager, ApplicationEventPublisher eventPublisher) {
	    	return new ApiErrorLogCaptureAppenderRegistrar(captureManager, eventPublisher);
	    }
    }
}
