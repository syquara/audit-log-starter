package com.auditlog.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to enable audit logging for application methods.
 *
 * <p>When applied to a method, it allows auditing aspects to capture and
 * persist information about the operation being executed, including request
 * and response data according to the configured settings.</p>
 *
 * <p>The generated audit records can be used for traceability, monitoring,
 * compliance, troubleshooting, and security analysis purposes.</p>
 *
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * Defines the business action being performed.
     *
     * <p>Examples: {@code CREATE_POLICY}, {@code UPDATE_CUSTOMER},
     * {@code DELETE_DOCUMENT}.</p>
     *
     * @return the action identifier associated with the audited operation
     */
    String action();

    /**
     * Defines the business domain or context associated with the operation.
     *
     * <p>Examples: {@code POLICY}, {@code CUSTOMER}, {@code CLAIM}.</p>
     *
     * @return the business domain related to the audited operation
     */
    String domain() default "";

    /**
     * Indicates whether the request data should be included in the audit log.
     *
     * @return {@code true} if request data should be logged; otherwise
     *         {@code false}
     */
    boolean logRequest() default true;

    /**
     * Indicates whether the response data should be included in the audit log.
     *
     * @return {@code true} if response data should be logged; otherwise
     *         {@code false}
     */
    boolean logResponse() default true;

}
