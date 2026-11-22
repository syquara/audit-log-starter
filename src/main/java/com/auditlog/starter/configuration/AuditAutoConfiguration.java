package com.auditlog.starter.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import com.auditlog.starter.aspect.AuditLogAspect;
import com.auditlog.starter.service.AuditPublisher;
import com.auditlog.starter.service.impl.DefaultAuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfiguration
public class AuditAutoConfiguration {
	
	@jakarta.annotation.Resource
	private ResourceLoader resourceLoader;

    @Bean
    public AuditPublisher auditPublisher(ObjectMapper objectMapper) {

        return new DefaultAuditPublisher(objectMapper);
    }

    @Bean
    public AuditLogAspect auditLogAspect(AuditPublisher publisher) {

        return new AuditLogAspect(publisher);
    }
 
    @Bean
    public LoadSensitiveFields loadSensitiveFields(ResourceLoader resourceLoader) {

    	return new LoadSensitiveFields(resourceLoader);
    }
    
}
