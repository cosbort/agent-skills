---
name: java-spring-best-practices
description: >
  Enterprise-grade Spring Boot 3.5.x/4.x development guide. Use when building
  Spring Boot APIs, microservices, or enterprise Java applications. Covers Java
  Records as DTOs, Virtual Threads, ProblemDetail (RFC 7807), RestClient,
  structured logging, Testcontainers, observability, sealed exception hierarchies,
  and GraalVM native. Triggers on: Spring Boot, Spring MVC, REST APIs in Java,
  microservices in Java, enterprise Java development.
license: MIT
metadata:
  version: "2.0.0"
  author: "Cosimo Bortone"
  spring-boot: "3.5.x / 4.0.x"
  java: "17+ (21+ recommended)"
---

# Java Spring Boot Best Practices v2.0

Production-ready Spring Boot guide. Updated for **Spring Boot 3.5.x / 4.0.x**, **Java 17+ (optimized for 21+)**.

## What's New in v2.0

- Java Records as DTOs (immutable by default)
- Virtual Threads for high-concurrency I/O (`spring.threads.virtual.enabled=true`)
- ProblemDetail (RFC 7807) for standardized error responses
- RestClient replaces RestTemplate (fluent, synchronous)
- Structured Logging built into Spring Boot 3.4+
- Testcontainers as primary integration testing strategy (replaces H2)
- Observability with Micrometer Observation API
- Docker Compose integration for dev environments
- Sealed classes for exception hierarchies
- GraalVM Native Image / CDS awareness

## Quick Start Checklist

- ✅ Java 17+ (21+ for Virtual Threads)
- ✅ `spring-boot-starter-parent` for dependency management
- ✅ Constructor injection with `@RequiredArgsConstructor`
- ✅ **Java Records for DTOs** (not mutable `@Data` classes)
- ✅ Controllers = routing only; Services = business logic
- ✅ ProblemDetail (RFC 7807) + `@RestControllerAdvice`
- ✅ `@Valid` + Bean Validation on record components
- ✅ SLF4J `{}` placeholders + structured logging
- ✅ YAML profiles (dev/prod/test)
- ✅ Unit tests (Mockito) + Integration tests (**Testcontainers**)
- ✅ `spring.threads.virtual.enabled=true`

## Core Architecture

```
controller/   → HTTP routing only (thin)
    ↓
service/      → Business logic, caching, validation
    ↓
repository/   → Spring Data JPA
    ↓
entity/       → JPA domain models
```

Each layer depends only on layers below. Controllers never access repositories directly. See [architecture.md](references/architecture.md) for layer-based vs feature-based patterns.

### Dependency Injection (Constructor Only)

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;
}
```

## Java Records as DTOs

Records replace `@Data` Lombok classes: immutable, concise, thread-safe.

```java
// Request (with validation)
public record CreateProductRequest(
    @NotBlank @Size(min = 3, max = 100) String name,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    @Size(max = 500) String description
) {}

// Response
public record ProductResponse(
    Long id, String name, BigDecimal price,
    String description, Instant createdAt
) {}
```

### Mapping

```java
@Component
public class ProductMapper {
    public ProductResponse toResponse(Product entity) {
        return new ProductResponse(entity.getId(), entity.getName(),
            entity.getPrice(), entity.getDescription(), entity.getCreatedAt());
    }

    public Product toEntity(CreateProductRequest req) {
        return Product.builder()
            .name(req.name()).price(req.price()).description(req.description()).build();
    }
}
```

### Controller with Records

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getAll(PageRequest.of(page, size)));
    }
}
```

See [ProductController.java](scripts/ProductController.java) and [ProductService.java](scripts/ProductService.java) for complete examples.

## Exception Handling (ProblemDetail + Sealed Classes)

### Enable RFC 7807

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

### Sealed Exception Hierarchy

```java
public sealed abstract class ApplicationException extends RuntimeException
    permits ResourceNotFoundException, DuplicateResourceException,
            BusinessRuleException, ExternalServiceException {

    private final HttpStatus httpStatus;
    protected ApplicationException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
    public HttpStatus getHttpStatus() { return httpStatus; }
}

public final class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found with id: %s".formatted(resource, id), HttpStatus.NOT_FOUND);
    }
}
```

### Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApp(ApplicationException ex) {
        log.warn("Application exception: {}", ex.getMessage());
        ProblemDetail p = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        p.setTitle(ex.getClass().getSimpleName());
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
```

**Response**: `{"type":"about:blank","title":"ResourceNotFoundException","status":404,"detail":"Product not found with id: 42","timestamp":"..."}`

See [exception-handling.md](references/exception-handling.md) for the full hierarchy, validation handler, and patterns.

## Virtual Threads (Java 21+)

One property makes blocking I/O (JDBC, REST calls) scalable without reactive programming:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
```

Tomcat, `@Async`, `@Scheduled` all use virtual threads automatically. Avoid `synchronized` blocks during I/O (causes pinning) — use `ReentrantLock` instead.

See [performance-guide.md](references/performance-guide.md) for pinning avoidance, connection pool sizing, and detection.

## RestClient (Replaces RestTemplate)

```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {
    private final RestClient restClient;

    public ProductResponse fetchProduct(Long id) {
        return restClient.get()
            .uri("/products/{id}", id)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                throw new ResourceNotFoundException("External product", id);
            })
            .body(ProductResponse.class);
    }
}
```

For declarative interfaces, use `@HttpExchange`. See [performance-guide.md](references/performance-guide.md) for configuration and timeouts.

## Logging

```java
@Slf4j
@Service
public class ProductService {
    public void process(Long id) {
        log.info("Processing product ID: {}", id);       // ✅ Placeholders
        log.error("Error processing product", exception); // ✅ Pass exception
    }
}
```

### Structured Logging (Spring Boot 3.4+)

```yaml
logging:
  structured:
    format:
      console: ecs    # Options: ecs, gelf, logstash
```

## Configuration

```yaml
spring:
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/db
    username: ${DB_USERNAME:dev}
    hikari:
      maximum-pool-size: 20
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
  mvc:
    problemdetails:
      enabled: true
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m
```

### Record-Based Config Properties

```java
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank String name,
    @Valid JwtProperties jwt
) {
    public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {}
}
```

See [config-reference.md](references/config-reference.md) for profiles, Docker Compose, and externalization.

## Testing

### Unit Tests (Mockito + AssertJ)

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository repository;
    @Mock private ProductMapper mapper;
    @InjectMocks private ProductService service;

    @Test
    void shouldThrow_whenNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

### Integration Tests (Testcontainers)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Widget","price":29.99,"description":"A widget"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Widget"));
    }
}
```

See [testing-guide.md](references/testing-guide.md) for comprehensive patterns, slice tests, and shared containers.

## Modern Java Idioms

```java
// .toList() instead of Collectors.toList() (Java 16+)
List<String> names = products.stream().map(Product::getName).toList();

// Pattern matching switch (Java 21+, exhaustive on sealed types)
return switch (ex) {
    case ResourceNotFoundException e -> "Missing: " + e.getMessage();
    case DuplicateResourceException e -> "Conflict: " + e.getMessage();
    case BusinessRuleException e -> "Rule: " + e.getMessage();
    case ExternalServiceException e -> "External: " + e.getMessage();
};

// Text blocks for JPQL
@Query("""
    SELECT p FROM Product p LEFT JOIN FETCH p.category
    WHERE p.price > :min ORDER BY p.createdAt DESC
    """)
List<Product> findExpensive(@Param("min") BigDecimal min);
```

See [streams-guide.md](references/streams-guide.md) for advanced patterns.

## Common Gotchas

| Mistake | Solution |
|---------|----------|
| `@Autowired` field injection | Constructor injection (`@RequiredArgsConstructor`) |
| Mutable `@Data` DTOs | Java Records |
| `Collectors.toList()` | `.toList()` (Java 16+) |
| H2 for integration tests | Testcontainers with real DB |
| Custom error JSON wrappers | ProblemDetail (RFC 7807) |
| RestTemplate in new code | RestClient |
| `synchronized` + virtual threads | `ReentrantLock` |
| `System.out.println()` | `@Slf4j` + placeholders |

## Reference Documentation

- **[Architecture](references/architecture.md)** — Layer vs feature-based structure
- **[Exception Handling](references/exception-handling.md)** — Sealed hierarchy, ProblemDetail
- **[Configuration](references/config-reference.md)** — Profiles, Docker Compose, records
- **[Streams & Java Idioms](references/streams-guide.md)** — Functional patterns, modern syntax
- **[Testing](references/testing-guide.md)** — Mockito, Testcontainers, slice tests
- **[Performance](references/performance-guide.md)** — Virtual threads, caching, observability

## Code Examples

- [ProductController.java](scripts/ProductController.java) — REST controller with records
- [ProductService.java](scripts/ProductService.java) — Service with business logic
- [GlobalExceptionHandler.java](scripts/GlobalExceptionHandler.java) — ProblemDetail handler
- [ProductServiceTest.java](scripts/ProductServiceTest.java) — Unit + IT patterns
- [pom-template.xml](scripts/pom-template.xml) — Complete Maven setup

## Design Principles

1. **Immutability First** — Records for DTOs/config, final fields everywhere
2. **Single Responsibility** — Each class does one thing
3. **Fail Fast** — Validate input early, throw meaningful exceptions
4. **Make Invalid States Impossible** — Sealed hierarchies, strong types, validation
5. **Observable by Default** — Structured logging, metrics, traces from day one

---

**Last Updated**: March 2026 | **Based on**: Spring Boot 3.5.x / 4.0.x, Java 17+ (21+ recommended)
