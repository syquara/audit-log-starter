package com.auditlog.starter.model;

import lombok.Builder;
import lombok.Data;

@Data
public class RequestData {
	
	private Authentication authentication;
	private Attributes attributes;
	private Object headers;
	private Object body;

	@Data
	@Builder
	public static class Authentication {
		private String userId;
		private String userName;
	}	
		
	@Data
	@Builder
	public static class Attributes {
		private String requestId;
		private String remoteAddr;
	}	
}


