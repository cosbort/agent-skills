# Configuration Reference v2.0

## Table of Contents
1. [Main Configuration](#main-configuration)
2. [Environment Profiles](#environment-profiles)
3. [Docker Compose Integration](#docker-compose-integration)
4. [Record-Based Configuration Properties](#record-based-configuration-properties)
5. [Externalize Sensitive Data](#externalize-sensitive-data)
6. [Property Priority](#property-priority)

## Main Configuration

### application.yml

```yaml
spring:
  application:
    name: my-service

  # Virtual Threads (Java 21+)
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true

  # Database (PostgreSQL)
  datasource:
    url: jdbc:postgresql://localhost:5432/app_db
    username: ${DB_USERNAME:dev}
    password: ${DB_PASSWORD:dev}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000

  # JPA / Hibernate
  jpa:
    hibernate:
      ddl-auto: validate              # Never 'create' in prod
    properties:
      hibernate:
        format_sql: false
        jdbc:
          batch_size: 30
          fetch_size: 50
        order_inserts: true
        order_updates: true
    show-sql: false
    open-in-view: false                # Prevent lazy loading issues

  # Cache (Caffeine for single-instance, Redis for distributed)
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m

  # Jackson
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false

  # RFC 7807 ProblemDetail
  mvc:
    problemdetails:
      enabled: true

# Server
server:
  port: 8080
  compression:
    enabled: true
    min-response-size: 1024
    mime-types:
      - application/json
      - text/html
  tomcat:
    threads:
      max: 200
      min-spare: 10

# Structured Logging (Spring Boot 3.4+)
logging:
  level:
    root: INFO
    com.company.app: DEBUG
  structured:
    format:
      console: ecs                     # Elastic Common Schema
      # Options: ecs, gelf, logstash

# Actuator & Observability
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: when-authorized
  observations:
    annotations:
      enabled: true
  tracing:
    sampling:
      probability: 1.0                # Reduce in production (e.g., 0.1)

# Custom Application Properties
app:
  name: My Service
  jwt:
    secret: ${JWT_SECRET:change-me-in-production}
    expiration: 24h
  api:
    timeout: 30s
    retry-attempts: 3
```

## Environment Profiles

### Profile-Specific Files

```
src/main/resources/
├── application.yml              # Shared configuration
├── application-dev.yml          # Development profile
├── application-prod.yml         # Production profile
├── application-test.yml         # Test profile
└── logback-spring.xml           # Custom logging (optional)
```

### Development Profile (application-dev.yml)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

  # Docker Compose auto-management
  docker:
    compose:
      lifecycle-management: start-and-stop

  devtools:
    restart:
      enabled: true

logging:
  level:
    root: INFO
    com.company.app: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

### Production Profile (application-prod.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/app_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}

logging:
  level:
    root: WARN
    com.company.app: INFO
  structured:
    format:
      console: logstash
      file: logstash

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  tracing:
    sampling:
      probability: 0.1
```

### Test Profile (application-test.yml)

With Testcontainers, you often don't need this file because `@ServiceConnection` auto-configures the datasource. But for fallback or non-container tests:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

  cache:
    type: simple

logging:
  level:
    root: WARN
    com.company.app: DEBUG
```

## Docker Compose Integration

Spring Boot 3.1+ auto-detects `compose.yaml` in your project root:

### compose.yaml

```yaml
services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: app_db
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  pgdata:
```

Spring Boot automatically connects to these services — no manual URL/port configuration needed in dev mode. The `spring-boot-docker-compose` dependency handles lifecycle management.

## Record-Based Configuration Properties

Use Java Records for immutable, validated configuration (Spring Boot 3.0+):

```java
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
    @NotBlank String name,
    @Valid JwtProperties jwt,
    @Valid ApiProperties api
) {
    public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration expiration
    ) {}

    public record ApiProperties(
        @NotNull Duration timeout,
        @Positive int retryAttempts
    ) {}
}
```

Enable on any `@Configuration` class:

```java
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {}
```

Usage:

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppProperties props;

    public String generateToken() {
        // props.jwt().secret(), props.jwt().expiration()
    }
}
```

Records provide immutability, validation at startup, and type-safe access — much better than scattering `@Value` annotations throughout the codebase.

## Externalize Sensitive Data

### Option 1: Environment Variables (Recommended)

```bash
export DB_USERNAME=prod_user
export DB_PASSWORD=securePassword
export JWT_SECRET=jwt_secret_key_prod

java -jar app.jar --spring.profiles.active=prod
```

### Option 2: Command-line Arguments

```bash
java -jar app.jar \
  --spring.profiles.active=prod \
  --DB_USERNAME=user \
  --DB_PASSWORD=pass
```

### Option 3: Kubernetes Secrets / ConfigMaps

```yaml
# k8s deployment.yaml
env:
  - name: DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: username
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: password
```

### Option 4: Spring Cloud Config (Microservices)

```yaml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      profile: prod
```

## Property Priority

Properties are loaded in this order (highest to lowest priority):

1. Command-line arguments
2. OS environment variables
3. System properties
4. `application-{profile}.yml`
5. `application.yml`
6. Default values in code (`${VAR:default}`)

### Activating Profiles

```bash
# Single profile
java -jar app.jar --spring.profiles.active=prod

# Multiple profiles
java -jar app.jar --spring.profiles.active=prod,oauth

# In tests
@SpringBootTest
@ActiveProfiles("test")
class MyTest {}
```

## Common Mistakes

❌ Hardcoded secrets in YAML files
❌ Using `@Value` for complex configuration (use records)
❌ `spring.jpa.open-in-view=true` (default, causes lazy-loading issues)
❌ No validation on required properties at startup
❌ Ignoring Hikari pool sizing with virtual threads
❌ MySQL dialect hardcoded instead of auto-detected

✅ Externalize all secrets via environment variables
✅ Use records with `@ConfigurationProperties` + `@Validated`
✅ Set `open-in-view: false` explicitly
✅ Validate configuration at startup (fail fast)
✅ Use Docker Compose integration for local development
✅ Monitor connection pool with Actuator metrics
