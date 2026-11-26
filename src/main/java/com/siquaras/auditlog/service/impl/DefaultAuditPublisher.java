package com.siquaras.auditlog.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.siquaras.auditlog.model.AuditData;
import com.siquaras.auditlog.service.AuditPublisher;
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
        	
            LOGGER.error("Failed to serialize audit event", e);

        }
    }
}
