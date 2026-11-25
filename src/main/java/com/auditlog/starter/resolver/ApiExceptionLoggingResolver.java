package com.auditlog.starter.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.auditlog.starter.service.ApiExceptionCaptureManager;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Observes exceptions raised while Spring MVC processes an API request.
 * Returning {@code null} leaves the exception unresolved so the application's
 * existing exception resolvers can produce the response as before.
 */
public class ApiExceptionLoggingResolver implements HandlerExceptionResolver, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionLoggingResolver.class);
    private final ApiExceptionCaptureManager captureManager;

    public ApiExceptionLoggingResolver(ApiExceptionCaptureManager captureManager) {
        this.captureManager = captureManager;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {

        if (!captureManager.shouldCaptureLogLevel("ERROR")) {
            return null;
        }

        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        try {
            String handlerName = resolveHandlerName(handler);
            String method = request == null ? "UNKNOWN" : request.getMethod();
            String path = request == null ? "UNKNOWN" : request.getRequestURI();
            String requestId = request == null ? "UNKNOWN" : request.getRequestId();
            LogEventSourceResolver.Source source = resolveHandlerSource(handler);

            MDC.put("audit.request.method", method);
            MDC.put("audit.request.path", path);
            MDC.put("audit.request.id", requestId);
            MDC.put("audit.request.handler", handlerName);
            MDC.put("audit.source.class", source.className());
            MDC.put("audit.source.method", source.methodName());

            LOGGER.error(
                    "API exception observed: type={}, method={}, path={}, requestId={}, handler={}, message={}",
                    exception.getClass().getName(),
                    method,
                    path,
                    requestId,
                    handlerName,
                    exception.getMessage(),
                    exception);
        } catch (RuntimeException ignored) {
            // The observer must never replace or suppress the original API exception.
        } finally {
            if (previousMdc == null) MDC.clear();
            else MDC.setContextMap(previousMdc);
        }

        return null;
    }

    private static String resolveHandlerName(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
        }
        return handler == null ? "UNKNOWN" : handler.getClass().getName();
    }

    private static LogEventSourceResolver.Source resolveHandlerSource(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return new LogEventSourceResolver.Source(
                    handlerMethod.getBeanType().getName(), handlerMethod.getMethod().getName());
        }
        return new LogEventSourceResolver.Source(
                handler == null ? "UNKNOWN" : handler.getClass().getName(), "UNKNOWN");
    }
}
