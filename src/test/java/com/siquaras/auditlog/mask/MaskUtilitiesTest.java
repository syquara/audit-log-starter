package com.siquaras.auditlog.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.siquaras.auditlog.configuration.LoadSensitiveFields;

class MaskUtilitiesTest {

    @BeforeAll
    static void loadConfiguredSensitiveField() throws Exception {
        ResourceLoader loader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        doReturn(resource).when(loader).getResource("classpath:sensitive_fields.txt");
        doReturn(new ByteArrayInputStream("private-key\n".getBytes(StandardCharsets.UTF_8))).when(resource).getInputStream();
        new LoadSensitiveFields(loader).init();
    }

    @Test
    void detectsValidAndInvalidSensitivePatterns() {
        assertTrue(MaskDetector.isCpf("12345678901"));
        assertFalse(MaskDetector.isCpf("123"));
        assertFalse(MaskDetector.isCpf(null));
        assertTrue(MaskDetector.isCnpj("12345678901234"));
        assertFalse(MaskDetector.isCnpj("12345678901"));
        assertFalse(MaskDetector.isCnpj(null));
        assertTrue(MaskDetector.isEmail("a@example.com"));
        assertFalse(MaskDetector.isEmail("invalid"));
        assertFalse(MaskDetector.isEmail(null));
        assertTrue(MaskDetector.isPhone("11987654321"));
        assertFalse(MaskDetector.isPhone("123"));
        assertFalse(MaskDetector.isPhone(null));
        assertTrue(MaskDetector.isJwt("header.payload.signature"));
        assertFalse(MaskDetector.isJwt("not-a-jwt"));
        assertFalse(MaskDetector.isJwt(null));
        assertTrue(MaskDetector.isBearer("BEARER token"));
        assertFalse(MaskDetector.isBearer("token"));
        assertFalse(MaskDetector.isBearer(null));
        assertTrue(MaskDetector.isSensitiveField("privateKey"));
        assertFalse(MaskDetector.isSensitiveField(null));
    }

    @Test
    void masksCnpjPhoneConfiguredFieldsAndEmailWithoutLocalPart() {
        assertEquals("*********34", AuditMaskEngine.mask("taxNumber", "12345678901234"));
        assertEquals("*********21", AuditMaskEngine.mask("phone", "11987654321"));
        assertEquals("***2345", AuditMaskEngine.mask("privateKey", "key-12345"));
        assertEquals("***", MaskUtil.maskEmail("@example.com"));
        assertEquals("***", MaskUtil.maskSensitiveFiled("abc"));
        assertEquals("***", MaskUtil.maskSensitiveFiled(null));
        assertEquals("***2345", MaskUtil.maskSensitiveFiled("secret-12345"));
        assertEquals("*********01", MaskUtil.maskCpf("12345678901"));
        assertEquals("*********34", MaskUtil.maskCnpj("12345678901234"));
        assertEquals("*******4321", MaskUtil.maskPhone("11987654321"));
    }
}
