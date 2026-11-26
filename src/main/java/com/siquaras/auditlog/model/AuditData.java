package com.siquaras.auditlog.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditData {

    private String action;

    private String domain;

    private String className;

    private String methodName;

    private Object request;

    private Object response;

    private Long executionTimeMillis;

    private Boolean success;

    private String error;

}