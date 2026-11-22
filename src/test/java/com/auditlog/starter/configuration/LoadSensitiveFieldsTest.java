package com.auditlog.starter.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class LoadSensitiveFieldsTest {

    @Test
    void loadsNormalizesAndExposesSensitiveFieldsAsReadOnly() throws Exception {
        ResourceLoader loader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        doReturn(resource).when(loader).getResource("classpath:sensitive_fields.txt");
        doReturn(new ByteArrayInputStream("  ChavE-Privada  \n\naccess_token\n".getBytes(StandardCharsets.UTF_8)))
                .when(resource).getInputStream();

        LoadSensitiveFields fields = new LoadSensitiveFields(loader);
        fields.init();

        assertTrue(LoadSensitiveFields.isSensitive("minha_chave_privada_value"));
        assertTrue(LoadSensitiveFields.isSensitive("ACCESS-TOKEN"));
        assertFalse(LoadSensitiveFields.isSensitive("ordinaryField"));
        assertThrows(UnsupportedOperationException.class,
                () -> LoadSensitiveFields.getSensitiveFields().add("new-field"));
    }

    @Test
    void toleratesMissingResource() throws Exception {
        ResourceLoader loader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        doReturn(resource).when(loader).getResource("classpath:sensitive_fields.txt");
        doReturnThrowingIOException(resource);

        new LoadSensitiveFields(loader).carregarSensiteFieldsTxt();
    }

    private static void doReturnThrowingIOException(Resource resource) throws IOException {
        org.mockito.Mockito.doThrow(new IOException("missing resource")).when(resource).getInputStream();
    }
}
