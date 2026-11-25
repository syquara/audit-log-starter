package com.siquaras.auditlog.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import com.siquaras.auditlog.aspect.AuditLogAspect;
import com.siquaras.auditlog.service.AuditPublisher;
import com.siquaras.auditlog.service.impl.DefaultAuditPublisher;
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
