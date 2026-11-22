package com.auditlog.starter.service;

import com.auditlog.starter.model.AuditData;

public interface AuditPublisher {

    void publish(AuditData auditData);

}
