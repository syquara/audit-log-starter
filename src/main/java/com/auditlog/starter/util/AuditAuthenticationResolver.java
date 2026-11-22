
package com.auditlog.starter.util;

import java.lang.reflect.Method;

import com.auditlog.starter.model.RequestData;

public class AuditAuthenticationResolver {

    public static RequestData.Authentication resolve() {

        String userId = Constants.UNKNOWN;
        String userName = Constants.UNKNOWN;

        try {
            Class<?> holderClass = Class.forName(
                "org.springframework.security.core.context.SecurityContextHolder"
            );

            Object context = holderClass
                .getMethod("getContext")
                .invoke(null);

            Object authentication = context.getClass()
                .getMethod("getAuthentication")
                .invoke(context);

            if (authentication == null) {
                return build(userId, userName);
            }

            Boolean authenticated = (Boolean) authentication.getClass()
                .getMethod("isAuthenticated")
                .invoke(authentication);

            if (!Boolean.TRUE.equals(authenticated)) {
                return build(userId, userName);
            }

            String authenticationName = (String) authentication.getClass()
                .getMethod("getName")
                .invoke(authentication);

            if (authenticationName != null
                    && !authenticationName.isBlank()
                    && !"anonymousUser".equals(authenticationName)) {
                userName = authenticationName;
            }

            Object principal = authentication.getClass()
                .getMethod("getPrincipal")
                .invoke(authentication);

            if (principal != null) {

                try {
                    Method claimMethod = principal.getClass()
                        .getMethod("getClaimAsString", String.class);

                    String jwtUserId = (String) claimMethod.invoke(
                        principal, Constants.USER_ID
                    );

                    String jwtUserName = (String) claimMethod.invoke(
                        principal, Constants.USER_NAME
                    );

                    if (jwtUserId != null && !jwtUserId.isBlank()) {
                        userId = jwtUserId;
                    }

                    if (jwtUserName != null && !jwtUserName.isBlank()) {
                        userName = jwtUserName;
                    }

                } catch (NoSuchMethodException ignored) {
                    // Principal não é um JWT.
                }
            }

        } catch (ClassNotFoundException ignored) {
            // Consumidor não utiliza Spring Security.
        } catch (Exception e) {
            // Falha na leitura da autenticação não deve impedir a auditoria.
        }

        return build(userId, userName);
    }

    private static RequestData.Authentication build(
            String userId, String userName) {

        return RequestData.Authentication.builder()
            .userId(userId)
            .userName(userName)
            .build();
    }
}
