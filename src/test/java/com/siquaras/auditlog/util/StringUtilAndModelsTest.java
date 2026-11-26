package com.siquaras.auditlog.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.siquaras.auditlog.model.AuditData;
import com.siquaras.auditlog.model.RequestData;
import com.siquaras.auditlog.model.ResponseData;

class StringUtilAndModelsTest {

    @Test
    void normalizesSeparatorsAndNull() {
        new StringUtil();
        assertEquals("secretkey", StringUtil.normalize(" Secret-Key "));
        assertEquals("accesstoken", StringUtil.normalize("access_token"));
        assertEquals("", StringUtil.normalize(null));
    }

    @Test
    void modelBuildersAndAccessorsRetainValues() {
        AuditData audit = AuditData.builder().action("UPDATE").domain("USER").className("Service")
                .methodName("update").request("input").response("output").executionTimeMillis(12L)
                .success(true).error(null).build();
        assertEquals("UPDATE", audit.getAction());
        audit.setAction("DELETE");
        assertEquals("DELETE", audit.getAction());
        assertEquals("USER", audit.getDomain());
        assertEquals("Service", audit.getClassName());
        assertEquals("update", audit.getMethodName());
        assertEquals("input", audit.getRequest());
        assertEquals("output", audit.getResponse());
        assertEquals(12L, audit.getExecutionTimeMillis());
        assertEquals(true, audit.getSuccess());
        assertNull(audit.getError());

        RequestData.Authentication authentication = RequestData.Authentication.builder()
                .userId("42").userName("ana").build();
        assertEquals("42", authentication.getUserId());
        authentication.setUserId("43");
        assertEquals("43", authentication.getUserId());
        assertEquals("ana", authentication.getUserName());

        RequestData.Attributes attributes = RequestData.Attributes.builder()
                .requestId("req-1").remoteAddr("127.0.0.1").build();
        assertEquals("req-1", attributes.getRequestId());
        attributes.setRequestId("req-2");
        assertEquals("req-2", attributes.getRequestId());
        assertEquals("127.0.0.1", attributes.getRemoteAddr());

        RequestData request = new RequestData();
        request.setAuthentication(authentication);
        request.setAttributes(attributes);
        request.setHeaders(Map.of("x", "y"));
        request.setBody(Map.of("id", 1));
        assertEquals(authentication, request.getAuthentication());
        assertEquals(attributes, request.getAttributes());
        assertEquals(Map.of("x", "y"), request.getHeaders());
        assertEquals(Map.of("id", 1), request.getBody());

        ResponseData response = ResponseData.builder().headers(Map.of("x", "y"))
                .body("body").statusCode("OK").statusCodeValue(200).build();
        assertEquals(Map.of("x", "y"), response.headers());
        assertEquals("body", response.body());
        assertEquals("OK", response.statusCode());
        assertEquals(200, response.statusCodeValue());
    }

    @Test
    void exposesConstants() {
        new Constants();
        assertEquals("UNKNOWN", Constants.UNKNOWN);
        assertEquals("userid", Constants.USER_ID);
        assertEquals("user_name", Constants.USER_NAME);
    }
}
