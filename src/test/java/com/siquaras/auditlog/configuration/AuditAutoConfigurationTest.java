package com.siquaras.auditlog.configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.siquaras.auditlog.aspect.AuditLogAspect;
import com.siquaras.auditlog.service.AuditPublisher;
import com.siquaras.auditlog.service.impl.DefaultAuditPublisher;
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

    @Test
    void backsOffWhenTheConsumerProvidesAnAuditPublisher() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConsumerPublisherConfiguration.class, AuditAutoConfiguration.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(AuditPublisher.class).size());
                    assertSame(context.getBean("consumerPublisher"), context.getBean(AuditPublisher.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerPublisherConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AuditPublisher consumerPublisher() { return event -> { }; }
    }

    private static AuditPublisher aspectPublisher(AuditLogAspect aspect) throws Exception {
        var field = AuditLogAspect.class.getDeclaredField("publisher");
        field.setAccessible(true);
        return (AuditPublisher) field.get(aspect);
    }
}
