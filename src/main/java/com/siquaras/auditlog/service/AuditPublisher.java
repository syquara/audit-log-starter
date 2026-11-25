package com.siquaras.auditlog.service;

import com.siquaras.auditlog.model.AuditData;

public interface AuditPublisher {

    void publish(AuditData auditData);

}
