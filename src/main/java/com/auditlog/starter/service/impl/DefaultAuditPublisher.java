package com.auditlog.starter.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auditlog.starter.model.AuditData;
import com.auditlog.starter.service.AuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;


public class DefaultAuditPublisher implements AuditPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAuditPublisher.class);

    private final ObjectMapper objectMapper;

    public DefaultAuditPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(AuditData auditData) {

        try {

            LOGGER.info("AUDIT={}", objectMapper.writeValueAsString(auditData));

        } catch (Exception e) {
        	
            LOGGER.error("Erro ao gerar audit", e);

        }
    }
}
