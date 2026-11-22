package com.auditlog.starter.mask;

public final class AuditMaskEngine {

    private AuditMaskEngine() {
    }

    public static Object mask(Object key, Object value) {

        if (key == null || value == null) {
            return null;
        }

        String keyParam = String.valueOf(key);
        String valueParam = String.valueOf(value);

        if (MaskDetector.isSensitiveField(keyParam)) {
            return MaskUtil.maskSensitiveFiled(valueParam);
        }

        if (MaskDetector.isCpf(keyParam) || MaskDetector.isCpf(valueParam)) {
            return MaskUtil.maskCpf(valueParam);
        }

        if (MaskDetector.isCnpj(keyParam) || MaskDetector.isCnpj(valueParam)) {
            return MaskUtil.maskCnpj(valueParam);
        }

        if (MaskDetector.isEmail(keyParam) || MaskDetector.isEmail(valueParam)) {
            return MaskUtil.maskEmail(valueParam);
        }

        if (MaskDetector.isPhone(keyParam) || MaskDetector.isPhone(valueParam)) {
            return MaskUtil.maskPhone(valueParam);
        }

        if (MaskDetector.isJwt(valueParam)) {
            return "***JWT***";
        }

        if (MaskDetector.isBearer(valueParam)) {
            return "***TOKEN***";
        }

        return value;
    }
}