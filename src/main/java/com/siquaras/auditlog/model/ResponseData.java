package com.siquaras.auditlog.model;

import lombok.Builder;

@Builder
public record ResponseData(
		Object headers,
		Object body,
		Object statusCode,
		Object statusCodeValue
		) {

}
