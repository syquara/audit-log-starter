package com.siquaras.auditlog.aspect;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Field;
import java.time.temporal.Temporal;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.siquaras.auditlog.annotation.AuditLog;
import com.siquaras.auditlog.mask.AuditObjectInspector;
import com.siquaras.auditlog.model.AuditData;
import com.siquaras.auditlog.model.RequestData;
import com.siquaras.auditlog.model.ResponseData;
import com.siquaras.auditlog.resolver.AuditAuthenticationResolver;
import com.siquaras.auditlog.service.AuditPublisher;
import com.siquaras.auditlog.util.Constants;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Adds audit records around annotated operations without owning their business
 * outcome.
 */
@Aspect
public class AuditLogAspect {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogAspect.class);
	private final AuditPublisher publisher;

	public AuditLogAspect(AuditPublisher publisher) {
		this.publisher = publisher;
	}

	@Around("@annotation(auditLog)")
	public Object execute(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
		
		long startedAt = System.currentTimeMillis();
		Object result;
		
		try {
			result = joinPoint.proceed();
		} catch (Throwable businessFailure) {
			
			publishFailure(joinPoint, auditLog, startedAt, businessFailure);
			throw businessFailure;
		}

		try {
			
			publisher.publish(AuditData.builder().action(auditLog.action()).domain(auditLog.domain())
					.className(joinPoint.getTarget().getClass().getName()).methodName(joinPoint.getSignature().getName())
					.request(auditLog.logRequest() ? obtainRequestData(joinPoint) : null)
					.response(auditLog.logResponse() ? obtainResponseData(result) : null)
					.executionTimeMillis(System.currentTimeMillis() - startedAt).success(true).build());
			
		} catch (Exception auditFailure) {
			LOGGER.warn("Audit event could not be published; business result will be returned.", auditFailure);
		}
		return result;
	}

	private void publishFailure(ProceedingJoinPoint joinPoint, AuditLog auditLog, long startedAt, Throwable failure) {
		
		try {
			
			publisher.publish(AuditData.builder().action(auditLog.action()).domain(auditLog.domain())
					.className(joinPoint.getTarget().getClass().getName()).methodName(joinPoint.getSignature().getName())
					.success(false).error(failure.getMessage()).executionTimeMillis(System.currentTimeMillis() - startedAt)
					.build());
			
		} catch (RuntimeException auditFailure) {
			LOGGER.warn("Audit event could not be published; original business failure will be rethrown.", auditFailure);
		}
	}

	private RequestData obtainRequestData(ProceedingJoinPoint joinPoint) throws Exception {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] parameterNames = signature.getParameterNames();
		Object[] parameterValues = joinPoint.getArgs();
		Map<String, Object> requestBody = new LinkedHashMap<>();
		
		for (int i = 0; i < parameterValues.length; i++) {
			String parameterName = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
			requestBody.put(parameterName, AuditObjectInspector.sanitize(parameterName, parameterValues[i]));
		}

		RequestData requestData = obtainHttpRequestData();
		requestData.setBody(requestBody);
		return requestData;
	}

	private RequestData obtainHttpRequestData() {
		String requestId = null;
		String remoteAddress = null;
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		
		if (attributes != null) {
			HttpServletRequest request = attributes.getRequest();
			
			if (request != null) {
				requestId = request.getRequestId();
				remoteAddress = request.getRemoteAddr();
			}
		}

		RequestData request = new RequestData();
		request.setAuthentication(AuditAuthenticationResolver.resolve());
		request.setAttributes(RequestData.Attributes.builder().requestId(Optional.ofNullable(requestId).orElse(Constants.UNKNOWN))
						.remoteAddr(Optional.ofNullable(remoteAddress).orElse(Constants.UNKNOWN)).build());
		return request;
	}

	private ResponseData obtainResponseData(Object response) throws Exception {
		HttpHeaders headers = null;
		HttpStatusCode statusCode = null;
		Object statusCodeValue = null;
		
		if (response instanceof ResponseEntity<?> responseEntity) {
			response = responseEntity.getBody();
			headers = responseEntity.getHeaders();
			statusCode = responseEntity.getStatusCode();
			statusCodeValue = statusCode.value();
		}

		Map<String, Object> body = sanitizeResponseBody(response);
		return ResponseData.builder().headers(headers).body(body).statusCode(statusCode)
				.statusCodeValue(statusCodeValue).build();
	}

	private Map<String, Object> sanitizeResponseBody(Object response) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		
		if (response == null) {
			return body;
		}
		
		if (isScalar(response.getClass()) || response instanceof Collection<?> || response instanceof Map<?, ?> || response.getClass().isArray()) {
			
			Object sanitized = AuditObjectInspector.sanitize("body", response);
						
			if (sanitized instanceof Map<?, ?> map) {
				map.forEach((key, value) -> body.put(String.valueOf(key), value));
			} else {
				body.put("value", sanitized);
			}
			
			return body;
		}
		
		for (Field field : response.getClass().getDeclaredFields()) {
			try {
				
				field.setAccessible(true);
				body.put(field.getName(), AuditObjectInspector.sanitize(field.getName(), field.get(response)));
				
			} catch (Exception inaccessibleOrUnserializableField) {
				LOGGER.debug("Skipping inaccessible response field '{}' during audit sanitization.", field.getName());
			}
		}
		
		return body;
	}

	private static boolean isScalar(Class<?> type) {
		
		return type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class
				|| type == Character.class || Temporal.class.isAssignableFrom(type) || type.isEnum();
	}
}
