package com.siquaras.auditlog.mask;

public final class AuditMaskEngine {

    private AuditMaskEngine() {
    }

    public static Object mask(Object key, Object value) {

        if (value == null) {
            return null;
        }

        String valueParam = String.valueOf(value);

        if (key != null && MaskDetector.isSensitiveField(String.valueOf(key))) {
            return MaskUtil.maskSensitiveFiled(valueParam);
        }

        if (MaskDetector.isCpf(valueParam)) {
            return MaskUtil.maskCpf(valueParam);
        }

        if (MaskDetector.isCnpj(valueParam)) {
            return MaskUtil.maskCnpj(valueParam);
        }

        if (MaskDetector.isEmail(valueParam)) {
            return MaskUtil.maskEmail(valueParam);
        }

        if (MaskDetector.isPhone(valueParam)) {
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
