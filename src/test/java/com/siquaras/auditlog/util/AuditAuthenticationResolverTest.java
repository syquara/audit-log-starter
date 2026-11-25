package com.siquaras.auditlog.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder.TestAuthentication;
import org.springframework.security.core.context.SecurityContextHolder.TestJwtPrincipal;
import org.springframework.security.core.context.SecurityContextHolder.TestSecurityContext;

import com.siquaras.auditlog.model.RequestData;
import com.siquaras.auditlog.resolver.AuditAuthenticationResolver;

import java.net.URL;
import java.net.URLClassLoader;

class AuditAuthenticationResolverTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.failOnGetContext(false);
        SecurityContextHolder.setContext(new TestSecurityContext(null));
    }

    @Test
    void returnsUnknownWhenAuthenticationIsMissingOrUnauthenticated() {
        assertAuthentication(null, "UNKNOWN", "UNKNOWN");
        assertAuthentication(new TestAuthentication(false, "user", "principal"), "UNKNOWN", "UNKNOWN");
    }

    @Test
    void usesAuthenticatedNameWhenPrincipalHasNoJwtClaims() {
        assertAuthentication(new TestAuthentication(true, "ana", "plain-principal"), "UNKNOWN", "ana");
        assertAuthentication(new TestAuthentication(true, null, null), "UNKNOWN", "UNKNOWN");
    }

    @Test
    void usesJwtClaimsAndFallsBackToAuthenticationName() {
        assertAuthentication(new TestAuthentication(true, "fallback", new TestJwtPrincipal()),
                "user-123", "jwt-user");
        assertAuthentication(new TestAuthentication(true, "anonymousUser", null),
                "UNKNOWN", "UNKNOWN");
        assertAuthentication(new TestAuthentication(true, " ", null), "UNKNOWN", "UNKNOWN");
        assertAuthentication(new TestAuthentication(true, "fallback", new SecurityContextHolder.TestBlankJwtPrincipal()),
                "UNKNOWN", "fallback");
    }

    @Test
    void containsFailuresWhileReadingSecurityContext() {
        new AuditAuthenticationResolver();
        SecurityContextHolder.failOnGetContext(true);
        assertAuthenticationResult("UNKNOWN", "UNKNOWN");
    }

    @Test
    void worksWhenSpringSecurityIsNotOnTheRuntimeClasspath() throws Exception {
        URL classes = AuditAuthenticationResolver.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader withoutSecurity = new URLClassLoader(new URL[] {classes}, getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(AuditAuthenticationResolver.class.getName())) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                if (name.equals("org.springframework.security.core.context.SecurityContextHolder")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> resolver = Class.forName(AuditAuthenticationResolver.class.getName(), true, withoutSecurity);
            RequestData.Authentication result = (RequestData.Authentication) resolver.getMethod("resolve").invoke(null);
            assertEquals("UNKNOWN", result.getUserId());
            assertEquals("UNKNOWN", result.getUserName());
        }
    }

    private static void assertAuthentication(Object authentication, String userId, String userName) {
        SecurityContextHolder.setContext(new TestSecurityContext(authentication));
        assertAuthenticationResult(userId, userName);
    }

    private static void assertAuthenticationResult(String userId, String userName) {
        RequestData.Authentication result = AuditAuthenticationResolver.resolve();
        assertEquals(userId, result.getUserId());
        assertEquals(userName, result.getUserName());
    }
}
