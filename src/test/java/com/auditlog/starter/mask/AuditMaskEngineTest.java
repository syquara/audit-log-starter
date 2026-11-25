package com.auditlog.starter.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AuditMaskEngineTest {

    @Test
    void masksRecognizedSensitiveValues() {
        assertEquals("*********89", AuditMaskEngine.mask("document", "12345678989"));
        assertEquals("j***@example.com", AuditMaskEngine.mask("contact", "john@example.com"));
        assertEquals("***TOKEN***", AuditMaskEngine.mask("authorization", "Bearer secret-token"));
        assertEquals("***JWT***", AuditMaskEngine.mask("token", "header.payload.signature"));
    }

    @Test
    void keepsOrdinaryValuesAndReturnsNullForNullInput() {
        assertEquals("hello", AuditMaskEngine.mask("message", "hello"));
        assertNull(AuditMaskEngine.mask("message", null));
        assertEquals("value", AuditMaskEngine.mask(null, "value"));
    }

    @Test
    void recognizesPatternsFromKeysAndSanitizesSimpleTypes() throws Exception {
        assertEquals("*********01", AuditMaskEngine.mask("12345678901", "12345678901"));
        assertEquals("*********34", AuditMaskEngine.mask("12345678901234", "12345678901234"));
        assertEquals("a***@example.com", AuditMaskEngine.mask("ab@example.com", "ab@example.com"));
        assertEquals("*******5432", AuditMaskEngine.mask("1198765432", "1198765432"));
        assertEquals("*******5432", AuditMaskEngine.mask("contact", "1198765432"));

        assertNull(AuditObjectInspector.sanitize("value", null));
        assertEquals(7, AuditObjectInspector.sanitize("count", 7));
        assertEquals(true, AuditObjectInspector.sanitize("enabled", true));
        assertEquals(LocalDate.of(2026, 1, 2), AuditObjectInspector.sanitize("date", LocalDate.of(2026, 1, 2)));
        assertEquals(Status.ACTIVE, AuditObjectInspector.sanitize("status", Status.ACTIVE));
    }

    @Test
    void sanitizesNestedObjectsCollectionsAndMaps() throws Exception {
        Map<?, ?> sanitized = (Map<?, ?>) AuditObjectInspector.sanitize(
                "request",
                Map.of("items", List.of(new Customer("12345678989", "john@example.com"))));

        List<?> items = (List<?>) sanitized.get("items");
        Map<?, ?> customer = (Map<?, ?>) items.get(0);
        assertEquals("*********89", customer.get("cpf"));
        assertEquals("j***@example.com", customer.get("email"));
    }

    @Test
    void preservesAndMasksSimpleValuesInsideCollectionsWithoutFieldNames() throws Exception {
        List<?> values = (List<?>) AuditObjectInspector.sanitize("items",
                List.of("ordinary", "12345678989", "john@example.com", 7));

        assertEquals(List.of("ordinary", "*********89", "j***@example.com", 7), values);
    }

    @Test
    void boundsCyclicObjectGraphsInsteadOfRecursingForever() throws Exception {
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);

        Map<?, ?> sanitized = (Map<?, ?>) AuditObjectInspector.sanitize("request", cyclic);
        assertEquals("[CIRCULAR_REFERENCE]", sanitized.get("self"));
    }

    @SuppressWarnings("unused")
    private static final class Customer {
		private final String cpf;
		private final String email;

        private Customer(String cpf, String email) {
            this.cpf = cpf;
            this.email = email;
        }
    }

    private enum Status { ACTIVE }
}
