package com.auditlog.starter.mask;

import java.util.regex.Pattern;

import com.auditlog.starter.configuration.LoadSensitiveFields;

public final class MaskDetector {

    private static final Pattern CPF = Pattern.compile("^\\d{11}$");

    private static final Pattern CNPJ = Pattern.compile("^\\d{14}$");

    private static final Pattern EMAIL = Pattern.compile( "^[A-Za-z0-9+_.-]+@(.+)$");

    private static final Pattern PHONE = Pattern.compile("^\\d{10,11}$");

    private static final Pattern JWT = Pattern.compile("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");
    
    private MaskDetector() {
    }

    public static boolean isCpf(String value) {
        return value != null && CPF.matcher(value).matches();
    }

    public static boolean isCnpj(String value) {
        return value != null && CNPJ.matcher(value).matches();
    }

    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }

    public static boolean isPhone(String value) {
        return value != null && PHONE.matcher(value).matches();
    }

    public static boolean isJwt(String value) {
        return value != null && JWT.matcher(value).matches();
    }

    public static boolean isBearer(String value) {
        return value != null && value.toLowerCase().startsWith("bearer ");
    }
    
    public static boolean isSensitiveField(String value) {
        return value != null && LoadSensitiveFields.isSensitive(value);
    }
    
}