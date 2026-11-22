package com.auditlog.starter.model;

import lombok.Builder;

@Builder
public record ResponseData(
		Object headers,
		Object body,
		Object statusCode,
		Object statusCodeValue
		) {

}
