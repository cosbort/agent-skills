# Exception Handling Guide v2.0

## Table of Contents
1. [Sealed Exception Hierarchy](#sealed-exception-hierarchy)
2. [ProblemDetail (RFC 7807)](#problemdetail-rfc-7807)
3. [Global Exception Handler](#global-exception-handler)
4. [Usage in Service Layer](#usage-in-service-layer)
5. [HTTP Status Code Mapping](#http-status-code-mapping)
6. [Best Practices](#best-practices)

## Sealed Exception Hierarchy

Java 17+ sealed classes make the exception hierarchy explicit and exhaustive. The compiler knows all possible subtypes, enabling pattern matching in switch expressions and preventing uncontrolled exception proliferation.

### Base Exception (Sealed)

```java
package com.company.app.exception;

import org.springframework.http.HttpStatus;

public sealed abstract class ApplicationException extends RuntimeException
    permits ResourceNotFoundException, DuplicateResourceException,
            BusinessRuleException, ExternalServiceException,
            UnauthorizedException, ForbiddenException {

    private final HttpStatus httpStatus;

    protected ApplicationException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected ApplicationException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
```

### Concrete Exception Types

```java
// Resource not found (404)
public final class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found with id: %s".formatted(resource, id),
              HttpStatus.NOT_FOUND);
    }
}

// Duplicate resource (409)
public final class DuplicateResourceException extends ApplicationException {
    public DuplicateResourceException(String resource, String field, Object value) {
        super("%s with %s '%s' already exists".formatted(resource, field, value),
              HttpStatus.CONFLICT);
    }
}

// Business rule violation (422)
public final class BusinessRuleException extends ApplicationException {
    public BusinessRuleException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

// External service error (503)
public final class ExternalServiceException extends ApplicationException {
    public ExternalServiceException(String service, String message) {
        super("Error calling %s: %s".formatted(service, message),
              HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ExternalServiceException(String service, String message, Throwable cause) {
        super("Error calling %s: %s".formatted(service, message),
              HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}

// Unauthorized (401)
public final class UnauthorizedException extends ApplicationException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}

// Forbidden (403)
public final class ForbiddenException extends ApplicationException {
    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
```

### Pattern Matching with Sealed Hierarchy (Java 21+)

Because the hierarchy is sealed, the switch is exhaustive — the compiler ensures all cases are covered:

```java
public String describeError(ApplicationException ex) {
    return switch (ex) {
        case ResourceNotFoundException e   -> "Missing: " + e.getMessage();
        case DuplicateResourceException e  -> "Conflict: " + e.getMessage();
        case BusinessRuleException e       -> "Rule violated: " + e.getMessage();
        case ExternalServiceException e    -> "External failure: " + e.getMessage();
        case UnauthorizedException e       -> "Auth required: " + e.getMessage();
        case ForbiddenException e          -> "Access denied: " + e.getMessage();
    };
}
```

## ProblemDetail (RFC 7807)

ProblemDetail provides a standardized JSON error format. Enable it globally:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

### ProblemDetail Fields

| Field | Type | Description |
|-------|------|-------------|
| `type` | URI | Problem type identifier (default: `about:blank`) |
| `title` | string | Human-readable summary |
| `status` | int | HTTP status code |
| `detail` | string | Specific explanation |
| `instance` | URI | Identifies the specific occurrence |
| custom fields | any | Extension properties via `setProperty()` |

### Response Example

```json
{
  "type": "about:blank",
  "title": "ResourceNotFoundException",
  "status": 404,
  "detail": "Product not found with id: 42",
  "instance": "/api/v1/products/42",
  "timestamp": "2026-03-13T10:30:00Z"
}
```

## Global Exception Handler

```java
package com.company.app.handler;

import com.company.app.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle all custom application exceptions.
     * The sealed hierarchy ensures this covers every known case.
     */
    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplicationException(ApplicationException ex) {
        log.warn("Application exception: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            ex.getHttpStatus(), ex.getMessage());
        problem.setTitle(ex.getClass().getSimpleName());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Handle Bean Validation errors from @Valid.
     * Returns a structured list of field-level errors.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields have invalid values");

        var errors = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .toList();
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Catch-all for unexpected exceptions.
     * Never leak internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGlobalException(Exception ex) {
        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatus(
            HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
```

## Usage in Service Layer

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductResponse getById(Long id) {
        return repository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public ProductResponse create(CreateProductRequest request) {
        if (repository.existsByName(request.name())) {
            throw new DuplicateResourceException("Product", "name", request.name());
        }
        Product entity = mapper.toEntity(request);
        return mapper.toResponse(repository.save(entity));
    }

    public ProductResponse update(Long id, CreateProductRequest request) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (!product.getName().equals(request.name())
                && repository.existsByName(request.name())) {
            throw new DuplicateResourceException("Product", "name", request.name());
        }

        product.setName(request.name());
        product.setPrice(request.price());
        product.setDescription(request.description());
        return mapper.toResponse(repository.save(product));
    }
}
```

## HTTP Status Code Mapping

| Status | Exception | Use Case |
|--------|-----------|----------|
| 400 | `MethodArgumentNotValidException` | Bean validation errors |
| 401 | `UnauthorizedException` | Missing or invalid authentication |
| 403 | `ForbiddenException` | Authenticated but not authorized |
| 404 | `ResourceNotFoundException` | Resource doesn't exist |
| 409 | `DuplicateResourceException` | Conflict (duplicate entry) |
| 422 | `BusinessRuleException` | Business rule violation |
| 500 | Uncaught `Exception` | Unexpected server error |
| 503 | `ExternalServiceException` | External service unavailable |

## Best Practices

1. **Use sealed classes** — The hierarchy is explicit, the compiler verifies coverage, and new exception types require conscious decisions
2. **Meaningful messages** — Include context (resource name, ID, field) in exception messages
3. **Log at the right level** — WARN for expected business exceptions, ERROR for unexpected failures
4. **HTTP status alignment** — Each exception maps to exactly one HTTP status
5. **Never expose internals** — Don't leak stack traces or implementation details to clients
6. **ProblemDetail for consistency** — All error responses follow RFC 7807, machine-readable and predictable
7. **Extension properties** — Use `setProperty()` for domain-specific data (timestamp, error codes, field lists)

## Anti-Patterns

❌ Catching generic `Exception` in business code
❌ Throwing `RuntimeException` with no context message
❌ Returning null to indicate errors
❌ Using exceptions for control flow
❌ Custom error JSON wrappers instead of ProblemDetail
❌ Non-sealed exception hierarchies that grow uncontrolled
❌ Exposing internal exception messages to API consumers
