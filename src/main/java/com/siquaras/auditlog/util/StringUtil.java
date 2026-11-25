package com.siquaras.auditlog.util;

import java.text.Normalizer;

public class StringUtil {

	public static String normalize(String value) {

	    if (value == null) {
	        return "";
	    }

	    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);

	    return normalized
	            .replaceAll("\\p{M}", "")
	            .replace("_", "")
	            .replace("-", "")
	            .replace(" ", "")
	            .toLowerCase();
	}
}
