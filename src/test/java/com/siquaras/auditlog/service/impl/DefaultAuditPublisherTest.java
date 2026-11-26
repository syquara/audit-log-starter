package com.siquaras.auditlog.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.siquaras.auditlog.model.AuditData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class DefaultAuditPublisherTest {

    @Test
    void serializesAndPublishesAuditData() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        doReturn("{\"action\":\"READ\"}").when(mapper).writeValueAsString(org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> new DefaultAuditPublisher(mapper).publish(
                AuditData.builder().action("READ").success(true).build()));
    }

    @Test
    void containsSerializationFailures() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("cannot serialize") { private static final long serialVersionUID = 1L; })
                .when(mapper).writeValueAsString(org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> new DefaultAuditPublisher(mapper).publish(AuditData.builder().build()));
    }
}
