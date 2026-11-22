package com.auditlog.starter.configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.auditlog.starter.aspect.AuditLogAspect;
import com.auditlog.starter.service.AuditPublisher;
import com.auditlog.starter.service.impl.DefaultAuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

class AuditAutoConfigurationTest {

    @Test
    void createsPublisherAspectAndSensitiveFieldsLoader() throws Exception {
        AuditAutoConfiguration configuration = new AuditAutoConfiguration();
        ObjectMapper mapper = new ObjectMapper();
        AuditPublisher publisher = configuration.auditPublisher(mapper);

        assertInstanceOf(DefaultAuditPublisher.class, publisher);
        AuditLogAspect aspect = configuration.auditLogAspect(publisher);
        assertNotNull(aspect);
        assertSame(publisher, aspectPublisher(aspect));
        assertNotNull(configuration.loadSensitiveFields(mock(org.springframework.core.io.ResourceLoader.class)));
    }

    private static AuditPublisher aspectPublisher(AuditLogAspect aspect) throws Exception {
        var field = AuditLogAspect.class.getDeclaredField("publisher");
        field.setAccessible(true);
        return (AuditPublisher) field.get(aspect);
    }
}
