package org.springframework.security.core.context;

/** Minimal test fixture for the optional, reflection based Spring Security integration. */
public final class SecurityContextHolder {
    private static Object context = new TestSecurityContext(null);
    private static boolean failOnGetContext;

    private SecurityContextHolder() { }

    public static Object getContext() {
        if (failOnGetContext) {
            throw new IllegalStateException("security context unavailable");
        }
        return context;
    }

    public static void setContext(Object newContext) {
        context = newContext;
    }

    public static void failOnGetContext(boolean fail) {
        failOnGetContext = fail;
    }

    public static final class TestSecurityContext {
        private final Object authentication;
        private boolean failOnRead;

        public TestSecurityContext(Object authentication) {
            this.authentication = authentication;
        }

        public Object getAuthentication() {
            if (failOnRead) {
                throw new IllegalStateException("context unavailable");
            }
            return authentication;
        }

        public void failOnRead() {
            failOnRead = true;
        }
    }

    public static final class TestAuthentication {
        private final boolean authenticated;
        private final String name;
        private final Object principal;

        public TestAuthentication(boolean authenticated, String name, Object principal) {
            this.authenticated = authenticated;
            this.name = name;
            this.principal = principal;
        }

        public boolean isAuthenticated() { return authenticated; }
        public String getName() { return name; }
        public Object getPrincipal() { return principal; }
    }

    public static final class TestJwtPrincipal {
        public String getClaimAsString(String claim) {
            return switch (claim) {
                case "userid" -> "user-123";
                case "user_name" -> "jwt-user";
                default -> null;
            };
        }
    }

    public static final class TestBlankJwtPrincipal {
        public String getClaimAsString(String claim) {
            return "userid".equals(claim) ? null : " ";
        }
    }
}
