package com.auditlog.starter.aspect;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.auditlog.starter.annotation.AuditLog;
import com.auditlog.starter.mask.AuditObjectInspector;
import com.auditlog.starter.model.AuditData;
import com.auditlog.starter.model.RequestData;
import com.auditlog.starter.model.ResponseData;
import com.auditlog.starter.service.AuditPublisher;
import com.auditlog.starter.util.AuditAuthenticationResolver;
import com.auditlog.starter.util.Constants;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
public class AuditLogAspect {

    private final AuditPublisher publisher;

    public AuditLogAspect(AuditPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("@annotation(auditLog)")
    public Object execute(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {

        long inicio = System.currentTimeMillis();

        try {

            Object result = joinPoint.proceed();

            publisher.publish(
                    AuditData.builder()
                            .action(auditLog.action())
                            .domain(auditLog.domain())
                            .classe(joinPoint.getTarget().getClass().getName())
                            .metodo(joinPoint.getSignature().getName())
                            .request(auditLog.logRequest() ? obterParametrosRequest(joinPoint) :  null)
                            .response(auditLog.logResponse() ? obterParametrosResponse(result) : null)
                            .tempoExecucao(System.currentTimeMillis() - inicio)
                            .sucesso(true)
                            .build());

            return result;

        } catch (Exception ex) {

            publisher.publish(
                    AuditData.builder()
                            .action(auditLog.action())
                            .domain(auditLog.domain())
                            .classe(joinPoint.getTarget().getClass().getName())
                            .metodo(joinPoint.getSignature().getName())
                            .sucesso(false)
                            .erro(ex.getMessage())
                            .tempoExecucao(System.currentTimeMillis() - inicio)
                            .build());

            throw ex;
        }
    }
    
    private RequestData obterParametrosRequest(ProceedingJoinPoint joinPoint) throws Throwable {
    	MethodSignature signature =  (MethodSignature) joinPoint.getSignature();
    	
    	String[] parameterNames = signature.getParameterNames();

    	Object[] parameterValues = joinPoint.getArgs();
    	
    	Map<String, Object> request = new LinkedHashMap<>();

    	for (int i = 0; i < parameterNames.length; i++) {

    	    request.put(parameterNames[i], AuditObjectInspector.sanitize(parameterNames[i], parameterValues[i]));
    	}
    	
    	RequestData requestData = obterInfosRequisicaoHTTP();
    	requestData.setBody(request);
    	
    	return requestData;
    }
    
    private RequestData obterInfosRequisicaoHTTP() {
    	String requestId = null;
		String remoteAddr = null;
		
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

		if(attributes != null) {
			requestId = attributes == null ? "0" : attributes.getRequest().getRequestId();
			
			HttpServletRequest request = attributes.getRequest();
		    
		    if (request != null){
		    	remoteAddr = request.getRemoteAddr();
		    }
		}
	    
		RequestData.Authentication auth = AuditAuthenticationResolver.resolve();
		
		RequestData.Attributes attr = RequestData.Attributes.builder()
															.requestId(Optional.ofNullable(requestId).orElse(Constants.UNKNOWN))
															.remoteAddr(Optional.ofNullable(remoteAddr).orElse(Constants.UNKNOWN))
															.build();
		
		RequestData request = new RequestData();
		request.setAuthentication(auth);
		request.setAttributes(attr);
		
		return request;
    }
    
    private ResponseData obterParametrosResponse(Object response) throws Throwable {
    	HttpHeaders headers = null;
    	HttpStatusCode statusCode = null;
    	Object statusCodeValue = null;
    	
    	if (response instanceof ResponseEntity<?> responseEntity) {
    	    response = responseEntity.getBody();
    	    headers = responseEntity.getHeaders();
    	    statusCode = responseEntity.getStatusCode();
    	    statusCodeValue = statusCode.value();
    	}

    	Map<String, Object> body = new LinkedHashMap<>();

    	if(response != null) {
    		for (Field field : response.getClass().getDeclaredFields()) {
    			field.setAccessible(true);
    			try {
    				body.put(
    						field.getName(),
    						AuditObjectInspector.sanitize(
    								field.getName(),
    								field.get(response)
    								)
    						);
				} catch (Exception e) {
					// Tratar campo não acessível.
				}
    		}
    	}
    	
    	return ResponseData.builder()
		    				.headers(headers)
		    				.body(body)
		    				.statusCode(statusCode)
		    				.statusCodeValue(statusCodeValue)
		    				.build();
    	
    }
}
