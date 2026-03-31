# Testing Guide v2.0

## Table of Contents
1. [Unit Testing](#unit-testing)
2. [Integration Testing with Testcontainers](#integration-testing-with-testcontainers)
3. [WebMvcTest (Slice Tests)](#webmvctest-slice-tests)
4. [Test Coverage](#test-coverage)
5. [Common Patterns](#common-patterns)
6. [Best Practices](#best-practices)

## Unit Testing

Unit tests verify individual components in isolation using mocks.

### Service Layer Test

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private ProductMapper mapper;

    @InjectMocks
    private ProductService service;

    private Product product;
    private ProductResponse response;

    @BeforeEach
    void setUp() {
        product = Product.builder()
            .id(1L)
            .name("Laptop")
            .price(BigDecimal.valueOf(999.99))
            .description("A laptop")
            .createdAt(Instant.now())
            .build();

        response = new ProductResponse(
            1L, "Laptop", BigDecimal.valueOf(999.99), "A laptop", Instant.now());
    }

    // --- Positive tests ---

    @Test
    void shouldReturnProduct_whenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(mapper.toResponse(product)).thenReturn(response);

        ProductResponse result = service.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Laptop");
        verify(repository).findById(1L);
        verify(mapper).toResponse(product);
    }

    @Test
    void shouldCreateProduct_whenNameIsUnique() {
        var request = new CreateProductRequest("Phone", BigDecimal.valueOf(599), "A phone");

        when(repository.existsByName("Phone")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(product);
        when(repository.save(any(Product.class))).thenReturn(product);
        when(mapper.toResponse(product)).thenReturn(response);

        ProductResponse result = service.create(request);

        assertThat(result).isNotNull();
        verify(repository).save(any(Product.class));
    }

    // --- Negative tests ---

    @Test
    void shouldThrow_whenProductNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Product not found");
    }

    @Test
    void shouldThrow_whenDuplicateName() {
        var request = new CreateProductRequest("Existing", BigDecimal.valueOf(100), null);
        when(repository.existsByName("Existing")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(DuplicateResourceException.class);

        verify(repository, never()).save(any());
    }
}
```

### Key Testing Assertions (AssertJ)

Prefer AssertJ over JUnit assertions — it's more readable and fluent:

```java
// AssertJ (recommended)
assertThat(result.name()).isEqualTo("Laptop");
assertThat(list).hasSize(3).extracting("name").containsExactly("A", "B", "C");
assertThatThrownBy(() -> service.getById(999L))
    .isInstanceOf(ResourceNotFoundException.class)
    .hasMessageContaining("not found");

// JUnit (less fluent, but fine for simple checks)
assertEquals("Laptop", result.name());
assertThrows(ResourceNotFoundException.class, () -> service.getById(999L));
```

## Integration Testing with Testcontainers

Testcontainers spins up real database instances in Docker, giving production-accurate tests. This is the recommended approach over H2, which hides SQL dialect differences and missing features.

### Prerequisites

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Full Integration Test

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductControllerIT {

    @Container
    @ServiceConnection  // Spring Boot 3.1+: auto-configures datasource URL, user, password
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository repository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        var request = new CreateProductRequest(
            "Widget", BigDecimal.valueOf(29.99), "A nice widget");

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Widget"))
            .andExpect(jsonPath("$.price").value(29.99))
            .andExpect(jsonPath("$.id").exists());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldGetProductById() throws Exception {
        Product saved = repository.save(Product.builder()
            .name("Existing").price(BigDecimal.valueOf(50)).build());

        mockMvc.perform(get("/api/v1/products/{id}", saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Existing"));
    }

    @Test
    void shouldReturn404_whenProductNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("ResourceNotFoundException"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn400_onValidationError() throws Exception {
        var invalid = new CreateProductRequest("", BigDecimal.valueOf(-1), null);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation Failed"))
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        Product saved = repository.save(Product.builder()
            .name("Old Name").price(BigDecimal.valueOf(100)).build());

        var update = new CreateProductRequest(
            "New Name", BigDecimal.valueOf(150), "Updated");

        mockMvc.perform(put("/api/v1/products/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        Product saved = repository.save(Product.builder()
            .name("To Delete").price(BigDecimal.valueOf(10)).build());

        mockMvc.perform(delete("/api/v1/products/{id}", saved.getId()))
            .andExpect(status().isNoContent());

        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    void shouldListProducts_withPagination() throws Exception {
        IntStream.rangeClosed(1, 25).forEach(i ->
            repository.save(Product.builder()
                .name("Product " + i).price(BigDecimal.valueOf(i)).build()));

        mockMvc.perform(get("/api/v1/products")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(10))
            .andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(3));
    }
}
```

### Shared Container for Faster Test Suites

If you have many IT classes, share a single container to avoid repeated Docker startup:

```java
// Abstract base class
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");
}

// Concrete test
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIT extends AbstractIntegrationTest {
    // ...tests inherit the shared container
}
```

### Oracle Database (for INPS/enterprise projects)

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>oracle-free</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@Container
@ServiceConnection
static OracleContainer oracle =
    new OracleContainer("gvenzl/oracle-free:23-slim-faststart");
```

## WebMvcTest (Slice Tests)

For testing controllers without loading the full application context:

```java
@WebMvcTest(ProductController.class)
class ProductControllerSliceTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ProductService service;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldReturnProduct_whenServiceFindsIt() throws Exception {
        var response = new ProductResponse(
            1L, "Widget", BigDecimal.valueOf(29.99), "desc", Instant.now());
        when(service.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/products/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void shouldReturn400_whenRequestBodyInvalid() throws Exception {
        var invalid = new CreateProductRequest("", null, null);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());
    }
}
```

Slice tests are faster than `@SpringBootTest` but only test the web layer. Combine with full integration tests for confidence.

## Test Coverage

### JaCoCo Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

```bash
mvn clean test
# Report: target/site/jacoco/index.html
```

### Coverage Goals

- **Line coverage**: >80%
- **Branch coverage**: >75%
- **Focus on**: service layer and exception paths

## Common Patterns

### Parameterized Tests

```java
@ParameterizedTest
@CsvSource({
    "100.00, true",
    "50.00, false",
    "1000.00, true"
})
void shouldClassifyExpensiveProducts(BigDecimal price, boolean expected) {
    var product = Product.builder().price(price).build();
    assertThat(service.isExpensive(product)).isEqualTo(expected);
}

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"  ", "\t"})
void shouldRejectBlankNames(String name) {
    var request = new CreateProductRequest(name, BigDecimal.ONE, null);
    // validate and assert
}
```

### Testing Async Code

```java
@Test
void shouldCompleteAsyncOperation() {
    CompletableFuture<ProductResponse> future = service.getProductAsync(1L);

    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(2))
        .satisfies(result -> assertThat(result.name()).isEqualTo("Laptop"));
}
```

### Testing Exception Messages

```java
@Test
void shouldIncludeResourceAndId_inNotFoundMessage() {
    assertThatThrownBy(() -> service.getById(999L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Product not found with id: 999");
}
```

## Best Practices

1. **Test names** — `shouldDoX_whenY` pattern: `shouldThrow_whenProductNotFound`
2. **Arrange-Act-Assert** — Clear structure in every test
3. **One behavior per test** — Each test verifies one scenario
4. **Mock external dependencies** — Database, HTTP clients, messaging
5. **Test positive AND negative paths** — Don't just test the happy path
6. **Testcontainers over H2** — Real DB catches real SQL issues
7. **AssertJ over JUnit assertions** — More readable, better error messages
8. **Use `var`** — Reduces noise in test code
9. **Shared containers** — Reuse Docker containers across test classes
10. **Keep tests fast** — <100ms per unit test, <5s per integration test

## Anti-Patterns

❌ Testing implementation details (verify internal method calls unless essential)
❌ Tests that depend on execution order
❌ H2 in-memory for integration tests (hides dialect differences)
❌ No cleanup between tests (`@BeforeEach` + `deleteAll()`)
❌ Asserting on too many things in one test
❌ Slow unit tests (usually means they're integration tests in disguise)
❌ Ignoring flaky tests instead of fixing them
