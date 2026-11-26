# audit-log-starter

`audit-log-starter` is a Spring Boot library that records structured audit events for business methods annotated with `@AuditLog`. It intercepts method execution, collects the configured operation data, and writes a JSON event to the application's SLF4J logger.

> **Version:** `0.2.2`  
> **Group ID:** `com.siquaras`  
> **Artifact ID:** `audit-log-starter`

## Features

- Intercepts methods annotated with `@AuditLog` using Spring AOP.
- Records the action, business domain, class, method, execution time, and outcome.
- Optionally records method arguments and return values.
- Resolves authentication details from Spring Security when available.
- Captures the remote address and request ID when an HTTP request is active.
- Masks configured sensitive fields and common sensitive value patterns.
- Emits structured JSON through SLF4J.
- Provides an opt-in, temporary in-memory capture API for selected Logback events and API exceptions.

When an audited method throws, the starter publishes a failure event and rethrows the original failure.

## Requirements and compatibility

| Component | Requirement |
|---|---|
| Java | 21 or later |
| Maven | 3.6.3 or later recommended |
| Spring Boot | 3.x; starter dependencies are managed with 3.5.16 |
| Spring Web | Required |
| Spring AOP | Required |
| Jackson Databind | Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`) |

This release uses Jackson 2 and Spring Boot 3.5.16. Spring Boot 4.x, which uses Jackson 3 by default, is not supported without adapting the library. The library is designed for Spring MVC/Servlet applications and uses `HttpServletRequest` to collect HTTP context.

The starter pins Tomcat `10.1.62`, Jackson `2.21.8`, and Log4j `2.26.1` to address dependency alerts. Spring Framework advisories CVE-2026-47884 and CVE-2026-47890 have no community patch compatible with Spring Boot 3.5.16 at this time. Review the [Spring advisory for CVE-2026-47884](https://spring.io/security/cve-2026-47884/) and [Spring advisory for CVE-2026-47890](https://spring.io/security/cve-2026-47890/) to determine whether the affected conditions apply to your application and follow their mitigations.

## Installation

Once version `0.2.2` is available from Maven Central, add this dependency:

```xml
<dependency>
    <groupId>com.siquaras</groupId>
    <artifactId>audit-log-starter</artifactId>
    <version>0.2.2</version>
</dependency>
```

For local development, install the artifact into your local Maven repository:

```bash
mvn clean install
```

The starter declares Spring Web, Spring AOP, Jackson Databind, H2, and Lombok. Spring Boot's dependency BOM manages Spring and Jackson versions; the project also manages its Tomcat and Log4j overrides. Review your application's dependency management for version conflicts.

## Configuration

Spring Boot loads `AuditAutoConfiguration` automatically through:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

No component scan for the starter's internal packages is required. The temporary exception and log capture integration is also auto-configured for Servlet applications.

### Sensitive fields

Create `src/main/resources/sensitive_fields.txt` in the consuming application and add one field name per line. Names are normalized before matching: accents, spaces, underscores, and hyphens are removed, and letters are converted to lowercase.

```text
password
token
authorization
creditCard
cardNumber
```

If the file is missing, the starter writes an error to the application log; application startup continues, but custom fields from that file are not masked. A configured name is matched within the field name, so `password` also matches `userPassword`.

This release does not expose custom `@ConfigurationProperties`. Select audited methods directly with `@AuditLog`.

## Auditing business methods

Annotate a Spring-managed method:

```java
import com.siquaras.auditlog.annotation.AuditLog;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    @AuditLog(action = "CREATE_CUSTOMER", domain = "CUSTOMER")
    public Customer createCustomer(CustomerRequest request) {
        return customerRepository.save(
            new Customer(request.name(), request.email())
        );
    }
}
```

`@AuditLog` supports these options:

| Option | Required | Default | Description |
|---|---:|---|---|
| `action` | Yes | — | Stable business action identifier, such as `CREATE_CUSTOMER`. |
| `domain` | No | `""` | Business domain or context, such as `CUSTOMER`. |
| `logRequest` | No | `true` | Include method arguments in the event. |
| `logResponse` | No | `true` | Include the return value in the event. |

To omit arguments and return values:

```java
@AuditLog(
    action = "DELETE_CUSTOMER",
    domain = "CUSTOMER",
    logRequest = false,
    logResponse = false
)
public void deleteCustomer(Long customerId) {
    // ...
}
```

The method must be invoked through its Spring proxy for AOP interception. Calls from one method to another method on the same object bypass the default Spring AOP proxy.

## Audit event format

The default `AuditPublisher` serializes an `AuditData` object with Jackson and writes it to SLF4J with the prefix `AUDIT=`. The event uses English JSON field names:

```json
{
  "action": "CREATE_CUSTOMER",
  "domain": "CUSTOMER",
  "className": "com.example.CustomerService",
  "methodName": "createCustomer",
  "executionTimeMillis": 18,
  "success": true,
  "error": null
}
```

The event can also include `request` and `response` objects. Request data can contain `authentication.userId`, `authentication.userName`, `attributes.requestId`, `attributes.remoteAddr`, and a sanitized `body` map. Missing HTTP or authentication context can produce the value `UNKNOWN`. Although `RequestData` has a `headers` property, this release does not populate it.

Authentication is resolved reflectively from Spring Security when available; `spring-boot-starter-security` is not a required dependency. If the principal provides `getClaimAsString`, the starter looks for `userid` and `user_name`. Other claim names are not configurable in this release.

For a `ResponseEntity`, the response data can contain `headers`, `statusCode`, `statusCodeValue`, and `body`. For other return types, the starter inspects fields declared directly on the returned class. Inherited fields are not guaranteed to be included.

You can provide your own `AuditPublisher` bean to route audit events elsewhere. The starter's default integration writes to SLF4J; it does not include a database or message broker publisher.

## Sensitive data masking

`AuditObjectInspector` and `AuditMaskEngine` mask fields by configured name and by recognized value patterns. Examples include:

| Input | Masked output |
|---|---|
| Configured field in `sensitive_fields.txt` | `***` plus the last four characters, or `***` for short values |
| 11-digit CPF | `*********` plus the last two digits |
| 14-digit CNPJ | `*********` plus the last two digits |
| Email address | First character, `***`, and the domain |
| 10- or 11-digit phone number | `*******` plus the last four digits |
| Recognized JWT | `***JWT***` |
| Value starting with `Bearer ` | `***TOKEN***` |

The inspector also traverses collections, maps, and nested object fields. Automatic masking is not a substitute for a data-protection policy. Review the DTOs and actual data sent to logs, avoid including unnecessary secrets or personal data, and verify the patterns used by your application before production use.

## Temporary API exception and log capture

The library provides an in-memory H2 session for temporarily capturing selected Logback events and API exceptions. Capture is disabled by default and can run for 1 to 3,600 seconds (120 seconds by default).

Start a session:

```http
PUT /auditLog/exceptionCapture
Content-Type: application/json

{
  "enabled": true,
  "levels": ["INFO", "ERROR"],
  "durationSeconds": 120
}
```

Accepted levels are `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, and `ALL`; provide at least one level. Use `GET /auditLog/exceptionCapture` to read the session status and `GET /auditLog/exceptionCapture/events?limit=100` to read events. The `limit` value ranges from 1 to 1,000 and defaults to 100. Send `{"enabled": false}` with `PUT` to stop the session early.

Available endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/auditLog/exceptionCapture` | Read capture status and counters. |
| `PUT` | `/auditLog/exceptionCapture` | Start or stop temporary capture. |
| `GET` | `/auditLog/exceptionCapture/events?limit=100` | Read the most recent events. |

The status response includes `enabled`, `levels`, `expiresAt`, `storedCount`, `storedBytes`, `droppedEvents`, `writeFailures`, `logCaptureAvailable`, and `loggingBackend`. Event records include `id`, `capturedAt`, `eventType`, `level`, `loggerName`, `threadName`, `message`, `exceptionType`, `exceptionMessage`, `stackTrace`, `requestMethod`, `requestPath`, `requestId`, `handler`, `sourceClass`, `sourceMethod`, and `eventJson`. Fields without available data may be `null`. `eventJson` is populated only when the entire log message is a JSON object or array.

The write queue holds up to 256 pending events. Messages are limited to 32 KiB UTF-8, exception messages to 8 KiB, and stack traces to 64 KiB. A session retains at most 10,000 records and an estimated 32 MiB, deleting older entries first. When a session stops or expires, its in-memory database is closed and its records are discarded.

Error responses use `400 Bad Request` for invalid payloads or limits and `503 Service Unavailable` when capture cannot be started or queried. Protect these endpoints with the consuming application's authentication and authorization policy.

## Error handling

- If an audited method throws, the aspect publishes a failure event and rethrows the same failure.
- If event serialization fails, the default publisher logs the error without propagating it to the business method.
- Authentication lookup failures do not prevent audit logging; available or default values are used.
- Failure to load `sensitive_fields.txt` is logged but does not prevent bean creation.
- Failures while creating or publishing an audit event do not change the business method's result or original exception. Fatal JVM errors can still interrupt the application.

## Best practices and troubleshooting

- Use stable, consistent values for `action` and `domain`.
- Audit important business operations rather than every method indiscriminately.
- Disable `logRequest` or `logResponse` when their contents are not needed.
- Keep `sensitive_fields.txt` with the consuming application and update it as DTOs change.
- Verify masking with representative data, including nested objects and collections.
- Configure log retention, access, and monitoring according to your security policies.
- Check that calls pass through a Spring proxy; same-instance calls are not intercepted by default.
- If a user is `UNKNOWN`, verify Spring Security context availability and the `userid` / `user_name` claims.
- If argument names are missing, compile with Java's `-parameters` option.
- Check Spring Boot, Spring Framework, and Jackson compatibility before upgrading dependencies.

## Build

Run the automated test suite with:

```bash
mvn test
```

Tests use JUnit 5 and Mockito. JaCoCo writes its report to `target/site/jacoco/index.html`. Temporary capture uses JDBC with an in-memory H2 database; no ORM entity package is used.

## Version history

### 0.2.2

- Added the MIT license and Maven Central publishing metadata.
- Standardized API event field names and user-facing messages in English.
- Updated project documentation to English.

### 0.2.1

- Updated Tomcat, Jackson, and Log4j dependencies with security fixes.
- Documented outstanding Spring advisories for the Spring Boot 3 compatible line.
- Kept compatibility with Java 21 and Spring Boot 3.5.16.

### 0.2.0

- Migrated the group ID to `com.siquaras` and Java packages to `com.siquaras.auditlog`.
- Organized internal packages by responsibility.
- Added temporary in-memory capture for logs and exceptions, controlled through an endpoint.
- Added `sourceClass` and `sourceMethod` for captured logs and exceptions.
- Added queue, event-size, and in-memory storage limits.
- Isolated audit failures from business results and exceptions.
- Added collection sanitization and circular-reference protection.

## License

This project is distributed under the [MIT License](LICENSE).
