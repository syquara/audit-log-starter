package com.auditlog.starter.mask;

public final class MaskUtil {

    private MaskUtil() {
    }

    public static String maskCpf(String cpf) {

        return "*********" + cpf.substring(cpf.length() - 2);
    }

    public static String maskCnpj(String cnpj) {

        return "*********" + cnpj.substring(cnpj.length() - 2);
    }

    public static String maskEmail(String email) {

        int pos = email.indexOf("@");

        if (pos <= 1) {
            return "***";
        }

        return email.charAt(0) + "***" + email.substring(pos);
    }

    public static String maskPhone(String phone) {

        return "*******" + phone.substring(phone.length() - 4);
    }

    public static String maskSensitiveFiled(String param) {

        if (param == null || param.length() < 4) {
            return "***";
        }
    	
        return "***" + param.substring(param.length() - 4);
    }
}