package com.siquaras.auditlog.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditData {

    private String action;

    private String domain;

    private String classe;

    private String metodo;

    private Object request;

    private Object response;

    private Long tempoExecucao;

    private Boolean sucesso;

    private String erro;

}