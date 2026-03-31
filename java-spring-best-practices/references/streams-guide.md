# Streams & Modern Java Idioms v2.0

## Table of Contents
1. [Stream Basics](#stream-basics)
2. [Common Operations](#common-operations)
3. [Collectors](#collectors)
4. [Modern Java Idioms (17+, 21+)](#modern-java-idioms)
5. [Collections Best Practices](#collections-best-practices)
6. [Performance Considerations](#performance-considerations)

## Stream Basics

Streams process data declaratively — they don't store elements, they compute on demand (lazy evaluation).

```java
List<String> names = products.stream()
    .filter(p -> p.getPrice().compareTo(BigDecimal.valueOf(100)) > 0)
    .map(Product::getName)
    .toList();  // ✅ Java 16+ (returns unmodifiable list)
```

### Creating Streams

```java
// From collection
Stream<Product> s1 = products.stream();

// From array
Stream<Product> s2 = Arrays.stream(productArray);

// Static factory
Stream<String> s3 = Stream.of("a", "b", "c");

// Range
IntStream range = IntStream.rangeClosed(1, 10);
```

## Common Operations

### Filter

```java
List<Product> expensive = products.stream()
    .filter(p -> p.getPrice().compareTo(BigDecimal.valueOf(100)) > 0)
    .filter(Product::isActive)
    .toList();
```

### Map

```java
List<String> names = products.stream()
    .map(Product::getName)
    .toList();

List<ProductResponse> dtos = products.stream()
    .map(mapper::toResponse)
    .toList();
```

### FlatMap

```java
// Flatten nested collections
List<Order> allOrders = customers.stream()
    .flatMap(c -> c.getOrders().stream())
    .toList();

List<String> allWords = sentences.stream()
    .flatMap(s -> Arrays.stream(s.split(" ")))
    .toList();
```

### Sorted

```java
// Ascending
products.stream()
    .sorted(Comparator.comparing(Product::getPrice))
    .toList();

// Descending, then by name
products.stream()
    .sorted(Comparator.comparing(Product::getPrice).reversed()
        .thenComparing(Product::getName))
    .toList();
```

### Reduce

```java
BigDecimal total = products.stream()
    .map(Product::getPrice)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

Optional<Product> mostExpensive = products.stream()
    .max(Comparator.comparing(Product::getPrice));
```

### Terminal Operations

```java
// Check conditions
boolean anyExpensive = products.stream()
    .anyMatch(p -> p.getPrice().compareTo(BigDecimal.valueOf(1000)) > 0);

boolean allInStock = products.stream()
    .allMatch(p -> p.getQuantity() > 0);

// Find
Optional<Product> first = products.stream()
    .filter(Product::isActive)
    .findFirst();
```

## Collectors

### groupingBy

```java
Map<String, List<Product>> byType = products.stream()
    .collect(Collectors.groupingBy(Product::getType));

Map<String, Long> countByType = products.stream()
    .collect(Collectors.groupingBy(
        Product::getType,
        Collectors.counting()
    ));

Map<String, BigDecimal> totalByType = products.stream()
    .collect(Collectors.groupingBy(
        Product::getType,
        Collectors.reducing(BigDecimal.ZERO, Product::getPrice, BigDecimal::add)
    ));
```

### partitioningBy

```java
Map<Boolean, List<Product>> partitioned = products.stream()
    .collect(Collectors.partitioningBy(
        p -> p.getPrice().compareTo(BigDecimal.valueOf(100)) > 0));
```

### joining

```java
String csv = products.stream()
    .map(Product::getName)
    .collect(Collectors.joining(", ", "[", "]"));
// [Laptop, Phone, Tablet]
```

### toMap

```java
Map<Long, Product> byId = products.stream()
    .collect(Collectors.toMap(Product::getId, Function.identity()));
```

### .toList() vs Collectors.toList()

```java
// ✅ Modern (Java 16+) — returns unmodifiable list
List<String> names = products.stream()
    .map(Product::getName)
    .toList();

// ⚠️ Legacy — returns mutable ArrayList
List<String> names = products.stream()
    .map(Product::getName)
    .collect(Collectors.toList());

// Use .toList() by default. Use collect(Collectors.toList())
// only if you need a mutable result list.
```

## Modern Java Idioms

### Pattern Matching for switch (Java 21+)

```java
public String describe(ApplicationException ex) {
    return switch (ex) {
        case ResourceNotFoundException e -> "Not found: " + e.getMessage();
        case DuplicateResourceException e -> "Conflict: " + e.getMessage();
        case BusinessRuleException e -> "Rule violated: " + e.getMessage();
        case ExternalServiceException e -> "External failure: " + e.getMessage();
    };
}
```

### Pattern Matching for instanceof (Java 16+)

```java
// ❌ Old style
if (obj instanceof String) {
    String s = (String) obj;
    process(s);
}

// ✅ Modern
if (obj instanceof String s) {
    process(s);
}
```

### Text Blocks (Java 15+)

```java
// ✅ Multi-line JPQL queries
@Query("""
    SELECT p FROM Product p
    LEFT JOIN FETCH p.category c
    WHERE p.price > :minPrice
      AND c.active = true
    ORDER BY p.createdAt DESC
    """)
List<Product> findExpensiveProducts(@Param("minPrice") BigDecimal minPrice);

// ✅ JSON templates in tests
String json = """
    {
        "name": "Widget",
        "price": 29.99,
        "description": "A useful widget"
    }
    """;
```

### Records as Local DTOs

```java
public List<ProductSummary> getProductSummaries() {
    // Local record — scoped to this method
    record ProductSummary(String name, BigDecimal price) {}

    return products.stream()
        .map(p -> new ProductSummary(p.getName(), p.getPrice()))
        .toList();
}
```

### String.formatted() (Java 15+)

```java
// ✅ Modern
String msg = "Product %s not found with id: %d".formatted(name, id);

// ❌ Legacy
String msg = String.format("Product %s not found with id: %d", name, id);
```

### Optional Chaining

```java
// ✅ Chain transformations
String categoryName = repository.findById(productId)
    .map(Product::getCategory)
    .map(Category::getName)
    .orElse("Uncategorized");

// ✅ Throw meaningful exception
Product product = repository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Product", id));

// ❌ Never use .get() — it defeats the purpose of Optional
product.get();  // throws NoSuchElementException with no context
```

## Collections Best Practices

### Program to Interface

```java
// ✅ Interface types
List<Product> items = new ArrayList<>();
Set<String> names = new HashSet<>();
Map<String, Product> byId = new HashMap<>();

// ❌ Concrete types
ArrayList<Product> items = new ArrayList<>();
```

### Pre-allocate When Size Is Known

```java
List<Product> items = new ArrayList<>(expectedSize);
Map<String, Product> map = HashMap.newHashMap(expectedSize);  // Java 19+
```

### Never Return Null Collections

```java
// ✅ Return empty collection
public List<Product> findByCategory(String category) {
    return products.stream()
        .filter(p -> p.getCategory().equals(category))
        .toList();  // Returns empty list if no matches
}

// ❌ Never return null
return null;  // Will cause NPE downstream
```

### Thread-Safe Collections

```java
// For concurrent access
Map<String, Product> concurrent = new ConcurrentHashMap<>();

// Or wrap existing collection
List<Product> syncList = Collections.synchronizedList(new ArrayList<>());
```

## Performance Considerations

### Sequential vs Parallel

```java
// Sequential (default) — use for most cases
List<Product> result = products.stream()
    .filter(this::isExpensive)
    .toList();

// Parallel — only for large collections (100k+) with expensive operations
List<Product> result = products.parallelStream()
    .filter(this::isExpensive)
    .toList();
```

Parallel streams have overhead (fork/join, thread synchronization). Only use them when the collection is large AND each operation is CPU-intensive.

### Avoid Intermediate Collections

```java
// ❌ Creates unnecessary intermediate list
List<String> names = products.stream()
    .map(Product::getName)
    .toList();
names.forEach(this::process);

// ✅ Chain directly
products.stream()
    .map(Product::getName)
    .forEach(this::process);
```

### Use Primitive Streams for Numeric Operations

```java
// ✅ Avoids boxing/unboxing
int totalQuantity = products.stream()
    .mapToInt(Product::getQuantity)
    .sum();

OptionalDouble avgPrice = products.stream()
    .mapToDouble(p -> p.getPrice().doubleValue())
    .average();
```
