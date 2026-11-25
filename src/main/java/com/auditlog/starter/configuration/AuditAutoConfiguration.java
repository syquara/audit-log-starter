package com.auditlog.starter.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import com.auditlog.starter.aspect.AuditLogAspect;
import com.auditlog.starter.service.AuditPublisher;
import com.auditlog.starter.service.impl.DefaultAuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfiguration
public class AuditAutoConfiguration {
	
	@ConditionalOnMissingBean(AuditPublisher.class)
    @Bean
    public AuditPublisher auditPublisher(ObjectMapper objectMapper) {

        return new DefaultAuditPublisher(objectMapper);
    }

	@ConditionalOnMissingBean(AuditLogAspect.class)
    @Bean
    public AuditLogAspect auditLogAspect(AuditPublisher publisher) {

        return new AuditLogAspect(publisher);
    }
 
	@ConditionalOnMissingBean(LoadSensitiveFields.class)
    @Bean
    public LoadSensitiveFields loadSensitiveFields(ResourceLoader resourceLoader) {

    	return new LoadSensitiveFields(resourceLoader);
    }
    
}
