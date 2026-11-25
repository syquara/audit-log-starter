package com.siquaras.auditlog.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.siquaras.auditlog.annotation.AuditLog;
import com.siquaras.auditlog.mask.AuditObjectInspector;
import com.siquaras.auditlog.model.AuditData;
import com.siquaras.auditlog.model.RequestData;
import com.siquaras.auditlog.model.ResponseData;
import com.siquaras.auditlog.service.AuditPublisher;

class AuditLogAspectTest {

    @Test
    void publishesSuccessfulAuditWithSanitizedRequestAndResponse() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        AuditedService target = new AuditedService();
        Result result = new Result("john@example.com");

        doReturn(target).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("createCustomer").when(signature).getName();
        doReturn(new String[] {"cpf"}).when(signature).getParameterNames();
        doReturn(new Object[] {"12345678989"}).when(joinPoint).getArgs();
        doReturn(result).when(joinPoint).proceed();

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        doReturn("request-123").when(httpRequest).getRequestId();
        doReturn("192.0.2.10").when(httpRequest).getRemoteAddr();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
        Object returned;
        try {
            returned = aspect.execute(joinPoint, auditAnnotation());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertSame(result, returned);
        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        AuditData audit = event.getValue();
        assertEquals("CREATE_CUSTOMER", audit.getAction());
        assertEquals("CUSTOMER", audit.getDomain());
        assertEquals("createCustomer", audit.getMetodo());
        assertTrue(audit.getSucesso());
        assertNotNull(audit.getTempoExecucao());

        RequestData request = (RequestData) audit.getRequest();
        assertEquals("*********89", ((java.util.Map<?, ?>) request.getBody()).get("cpf"));
        assertEquals("request-123", request.getAttributes().getRequestId());
        assertEquals("192.0.2.10", request.getAttributes().getRemoteAddr());
        ResponseData response = (ResponseData) audit.getResponse();
        assertEquals("j***@example.com", ((java.util.Map<?, ?>) response.body()).get("email"));
    }

    @Test
    void publishesFailureAndRethrowsTheOriginalException() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("createCustomer").when(signature).getName();
        IllegalArgumentException failure = new IllegalArgumentException("invalid customer");
        doThrow(failure).when(joinPoint).proceed();

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> aspect.execute(joinPoint, auditAnnotation())));

        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        assertFalse(event.getValue().getSucesso());
        assertEquals("invalid customer", event.getValue().getErro());
        assertNull(event.getValue().getRequest());
        assertNull(event.getValue().getResponse());
    }

    @Test
    void publisherFailureDoesNotChangeSuccessfulBusinessResult() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage down"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.any(AuditData.class));
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(mock(MethodSignature.class)).when(joinPoint).getSignature();
        Result result = new Result("john@example.com");
        doReturn(result).when(joinPoint).proceed();

        assertSame(result, aspect.execute(joinPoint, disabledPayloadAnnotation()));
    }

    @Test
    void publisherFailureDoesNotReplaceOriginalBusinessException() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage down"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.any(AuditData.class));
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(mock(MethodSignature.class)).when(joinPoint).getSignature();
        IllegalArgumentException businessFailure = new IllegalArgumentException("business validation failed");
        doThrow(businessFailure).when(joinPoint).proceed();

        assertSame(businessFailure, assertThrows(IllegalArgumentException.class,
                () -> aspect.execute(joinPoint, auditAnnotation())));
    }

    @Test
    void honorsDisabledRequestAndResponseAndHandlesNullResult() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("withoutPayload").when(signature).getName();
        doReturn(null).when(joinPoint).proceed();

        aspect.execute(joinPoint, disabledPayloadAnnotation());

        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        assertNull(event.getValue().getRequest());
        assertNull(event.getValue().getResponse());
    }

    @Test
    void capturesResponseEntityHeadersBodyAndStatus() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("created").when(signature).getName();
        ResponseEntity<Result> result = ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Audit", "yes").body(new Result("john@example.com"));
        doReturn(result).when(joinPoint).proceed();

        aspect.execute(joinPoint, responseEntityAnnotation());

        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        ResponseData response = (ResponseData) event.getValue().getResponse();
        assertEquals(201, response.statusCodeValue());
        assertEquals(HttpStatus.CREATED, response.statusCode());
        assertEquals("yes", ((org.springframework.http.HttpHeaders) response.headers()).getFirst("X-Audit"));
        assertEquals("j***@example.com", ((java.util.Map<?, ?>) response.body()).get("email"));
    }

    @Test
    void usesUnknownRequestAttributesWithoutAnHttpRequestAndHandlesNullResponseBody() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("createCustomer").when(signature).getName();
        doReturn(new String[] {"cpf"}).when(signature).getParameterNames();
        doReturn(new Object[] {"12345678989"}).when(joinPoint).getArgs();
        doReturn(null).when(joinPoint).proceed();

        aspect.execute(joinPoint, auditAnnotation());

        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        RequestData request = (RequestData) event.getValue().getRequest();
        assertEquals("UNKNOWN", request.getAttributes().getRequestId());
        assertEquals("UNKNOWN", request.getAttributes().getRemoteAddr());
        assertEquals(java.util.Map.of(), ((ResponseData) event.getValue().getResponse()).body());
    }

    @Test
    void skipsAResponseFieldWhenSanitizationThrows() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("created").when(signature).getName();
        doReturn(new Result("john@example.com")).when(joinPoint).proceed();

        try (org.mockito.MockedStatic<AuditObjectInspector> inspector = org.mockito.Mockito.mockStatic(AuditObjectInspector.class)) {
            inspector.when(() -> AuditObjectInspector.sanitize("email", "john@example.com"))
                    .thenThrow(new IllegalAccessException("cannot inspect"));
            aspect.execute(joinPoint, responseEntityAnnotation());
        }

        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        assertEquals(java.util.Map.of(), ((ResponseData) event.getValue().getResponse()).body());
    }

    @Test
    void toleratesRequestAttributesWithNoRequestOnSecondRead() throws Throwable {
        AuditPublisher publisher = mock(AuditPublisher.class);
        AuditLogAspect aspect = new AuditLogAspect(publisher);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        doReturn(new AuditedService()).when(joinPoint).getTarget();
        doReturn(signature).when(joinPoint).getSignature();
        doReturn("createCustomer").when(signature).getName();
        doReturn(new String[] {"cpf"}).when(signature).getParameterNames();
        doReturn(new Object[] {"12345678989"}).when(joinPoint).getArgs();
        doReturn(null).when(joinPoint).proceed();

        HttpServletRequest firstRead = mock(HttpServletRequest.class);
        doReturn("request-456").when(firstRead).getRequestId();
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(firstRead, (HttpServletRequest) null);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            aspect.execute(joinPoint, auditAnnotation());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        org.mockito.ArgumentCaptor<AuditData> event = org.mockito.ArgumentCaptor.forClass(AuditData.class);
        verify(publisher).publish(event.capture());
        RequestData request = (RequestData) event.getValue().getRequest();
        assertEquals("request-456", request.getAttributes().getRequestId());
        assertEquals("UNKNOWN", request.getAttributes().getRemoteAddr());
    }

    private static AuditLog auditAnnotation() throws NoSuchMethodException {
        return AuditedService.class.getDeclaredMethod("createCustomer", String.class)
                .getAnnotation(AuditLog.class);
    }

    private static AuditLog disabledPayloadAnnotation() throws NoSuchMethodException {
        return AuditedService.class.getDeclaredMethod("withoutPayload")
                .getAnnotation(AuditLog.class);
    }

    private static AuditLog responseEntityAnnotation() throws NoSuchMethodException {
        return AuditedService.class.getDeclaredMethod("created")
                .getAnnotation(AuditLog.class);
    }

    private static class AuditedService {
        @AuditLog(action = "CREATE_CUSTOMER", domain = "CUSTOMER")
        public Result createCustomer(String cpf) {
            return new Result("john@example.com");
        }

        @AuditLog(action = "NO_PAYLOAD", logRequest = false, logResponse = false)
        public Object withoutPayload() { return null; }

        @AuditLog(action = "CREATE_RESPONSE", logRequest = false)
        public ResponseEntity<Result> created() { return null; }
    }

    @SuppressWarnings("unused")
    private static class Result {
		private final String email;

        private Result(String email) {
            this.email = email;
        }
    }
}
