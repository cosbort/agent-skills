# Performance Optimization Guide v2.0

## Table of Contents
1. [Virtual Threads](#virtual-threads)
2. [Caching Strategy](#caching-strategy)
3. [Database Optimization](#database-optimization)
4. [API Response Optimization](#api-response-optimization)
5. [RestClient Configuration](#restclient-configuration)
6. [Observability & Monitoring](#observability--monitoring)
7. [GraalVM Native & CDS](#graalvm-native--cds)
8. [Common Bottlenecks](#common-bottlenecks)

## Virtual Threads

Virtual Threads (Java 21+, Project Loom) are the single biggest performance lever for I/O-bound Spring Boot apps. They allow millions of concurrent tasks without thread pool tuning.

### Configuration

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
```

This automatically configures Tomcat/Jetty, `@Async`, `@Scheduled`, and messaging listeners to use virtual threads.

### Impact on Connection Pools

With virtual threads, concurrency can spike dramatically. Adjust connection pools accordingly:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50      # May need increase with VT
      minimum-idle: 10
      connection-timeout: 20000
```

The bottleneck shifts from thread count to database connection count. Monitor connection pool metrics carefully.

### Avoiding Thread Pinning

Virtual threads get "pinned" to platform threads inside `synchronized` blocks. Replace with `ReentrantLock`:

```java
// ❌ Pins the virtual thread
public synchronized String fetchData() {
    return restClient.get().uri("/data").retrieve().body(String.class);
}

// ✅ Virtual-thread friendly
private final ReentrantLock lock = new ReentrantLock();

public String fetchData() {
    lock.lock();
    try {
        return restClient.get().uri("/data").retrieve().body(String.class);
    } finally {
        lock.unlock();
    }
}
```

### Detecting Pinning

```bash
# JVM flag to detect pinning at runtime
java -Djdk.tracePinnedThreads=short -jar app.jar
```

## Caching Strategy

### Caffeine Cache (Recommended for single-instance)

Caffeine is the fastest in-process cache for Java and the recommended default over `ConcurrentMapCacheManager`:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m
    cache-names: products,users
```

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("products", "users");
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats());  // Enable metrics
        return manager;
    }
}
```

### Redis Cache (Distributed / Multi-Instance)

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
```

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
```

### Caching Annotations

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getById(Long id) {
        return repository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @CacheEvict(value = "products", key = "#id")
    public ProductResponse update(Long id, CreateProductRequest request) {
        // ... update logic
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse create(CreateProductRequest request) {
        // ... create logic
    }

    // Conditional caching
    @Cacheable(value = "products", key = "#id",
               condition = "#id > 0",
               unless = "#result.price().compareTo(BigDecimal.valueOf(10000)) > 0")
    public ProductResponse getByIdConditional(Long id) {
        // Only cache if id > 0 and result price <= 10000
    }
}
```

## Database Optimization

### Connection Pool (HikariCP)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000    # 20s to acquire connection
      idle-timeout: 300000         # 5m before closing idle
      max-lifetime: 1200000        # 20m max connection lifetime
```

### Avoiding N+1 Queries

```java
// ❌ N+1 problem: 1 query for products + N queries for categories
List<Product> products = repository.findAll();
products.forEach(p -> log.info("Category: {}", p.getCategory().getName()));

// ✅ JOIN FETCH: single query
@Query("""
    SELECT p FROM Product p
    LEFT JOIN FETCH p.category
    """)
List<Product> findAllWithCategory();

// ✅ EntityGraph annotation
@EntityGraph(attributePaths = {"category", "tags"})
List<Product> findAll();
```

### Batch Processing

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 30
          fetch_size: 50
        order_inserts: true
        order_updates: true
```

### Always Paginate

```java
@GetMapping
public ResponseEntity<Page<ProductResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "id") String sort) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
    return ResponseEntity.ok(service.getAll(pageable));
}

// ❌ Never fetch all without limit
List<Product> all = repository.findAll();  // Dangerous at scale!
```

## API Response Optimization

### Response Compression

```yaml
server:
  compression:
    enabled: true
    min-response-size: 1024
    mime-types:
      - application/json
      - text/html
```

### JSON Serialization

```yaml
spring:
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
      indent-output: false    # Minimize JSON size in production
```

## RestClient Configuration

Configure timeouts and error handling for outbound HTTP calls:

```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(clientHttpRequestFactory())
            .build();
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
```

### Declarative HTTP Interface

```java
public interface ExternalProductClient {

    @GetExchange("/products/{id}")
    ProductResponse getProduct(@PathVariable Long id);

    @GetExchange("/products")
    List<ProductResponse> getAllProducts();
}
```

## Observability & Monitoring

### Spring Boot Actuator + Micrometer

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
  tracing:
    sampling:
      probability: 1.0   # 100% in dev, 0.1 in prod
  observations:
    annotations:
      enabled: true
```

### Custom Observations

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ObservationRegistry observationRegistry;
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductResponse getById(Long id) {
        return Observation.createNotStarted("product.fetch", observationRegistry)
            .lowCardinalityKeyValue("product.operation", "getById")
            .observe(() -> repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id)));
    }
}
```

### Custom Timer Metric

```java
@Component
@RequiredArgsConstructor
public class ProductMetrics {
    private final MeterRegistry meterRegistry;

    public void recordFetchDuration(long durationMs) {
        Timer.builder("product.fetch.duration")
            .description("Time to fetch a product")
            .tag("source", "database")
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }

    public void incrementCreationCounter() {
        meterRegistry.counter("product.created.total").increment();
    }
}
```

## GraalVM Native & CDS

### Class Data Sharing (CDS) — Easy Win

CDS pre-processes class metadata to accelerate JVM startup. No code changes required:

```bash
# Training run: generates archive
java -XX:ArchiveClassesAtExit=app-cds.jsa -jar app.jar
# (let app start, then stop it)

# Production run: uses archive for faster startup
java -XX:SharedArchiveFile=app-cds.jsa -jar app.jar
```

Spring Boot 3.3+ includes improved AOT processing for CDS.

### GraalVM Native Image

For serverless or CLI applications where startup time is critical:

```bash
# Maven
mvn -Pnative native:compile

# Gradle
gradle nativeCompile
```

Native images start in ~50ms but require AOT-compatible code (limited reflection). CDS is often a better first step for traditional services.

## Common Bottlenecks

### 1. Synchronous I/O Without Virtual Threads

```java
// ❌ Two sequential blocking calls on a platform thread
public OrderDTO getOrderWithDetails(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    Customer customer = customerClient.getCustomer(order.getCustomerId()); // Blocks
    return mapper.toDTO(order, customer);
}

// ✅ With virtual threads enabled, this scales automatically
// ✅ Or parallelize independent calls
public OrderDTO getOrderWithDetails(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    CompletableFuture<Customer> customerFuture =
        CompletableFuture.supplyAsync(() ->
            customerClient.getCustomer(order.getCustomerId()));
    return mapper.toDTO(order, customerFuture.join());
}
```

### 2. Inefficient Data Structures

```java
// ❌ Nested loop (O(n²))
for (Product p : products) {
    for (String keyword : keywords) {
        if (p.getName().contains(keyword)) { /* match */ }
    }
}

// ✅ Set lookup (O(n))
Set<String> keywordSet = new HashSet<>(keywords);
products.stream()
    .filter(p -> keywordSet.stream().anyMatch(k -> p.getName().contains(k)))
    .toList();
```

### 3. Excessive Object Creation in Loops

```java
// ❌ Creating StringBuilder in loop
for (Product p : products) {
    String key = new StringBuilder("product:").append(p.getId()).toString();
}

// ✅ Direct concatenation (JVM optimizes small cases)
for (Product p : products) {
    String key = "product:" + p.getId();
}
```

## Performance Checklist

✅ Enable virtual threads (Java 21+)
✅ Use Caffeine cache (single-instance) or Redis (distributed)
✅ Always paginate large datasets
✅ Eager-load associations (JOIN FETCH) to avoid N+1
✅ Enable batch inserts/updates (Hibernate batch_size)
✅ Enable response compression
✅ Configure RestClient timeouts
✅ Expose Prometheus metrics via Actuator
✅ Use CDS for faster JVM startup
✅ Profile regularly (JFR, async-profiler)
✅ Monitor connection pool saturation with virtual threads
