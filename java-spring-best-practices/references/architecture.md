# Architecture Patterns

## Table of Contents
1. [Layer-Based Structure](#layer-based-structure)
2. [Feature-Based Structure](#feature-based-structure)
3. [Choosing Between Patterns](#choosing-between-patterns)
4. [Package Organization](#package-organization)

## Layer-Based Structure

Best for **small to medium microservices** and simple applications.

```
src/main/java/com/company/app/
├── controller/
│   ├── ProductController.java
│   └── UserController.java
├── service/
│   ├── ProductService.java
│   └── UserService.java
├── repository/
│   ├── ProductRepository.java
│   └── UserRepository.java
├── entity/
│   ├── Product.java
│   └── User.java
├── dto/
│   ├── ProductRequestDTO.java
│   ├── ProductResponseDTO.java
│   └── UserDTO.java
├── exception/
│   ├── ProductNotFoundException.java
│   └── ValidationException.java
├── handler/
│   └── GlobalExceptionHandler.java
├── config/
│   ├── AppConfig.java
│   └── CacheConfig.java
└── SpringBootApp.java
```

### Benefits
- Easy to understand structure for small teams
- Clear separation of concerns by layer
- Straightforward navigation
- Suitable for monolithic applications

### Drawbacks
- Can become cluttered in large applications
- Related features scattered across packages
- Namespace collisions in large projects

## Feature-Based Structure

Best for **large enterprise applications** and microservices with multiple features.

```
src/main/java/com/company/app/
├── product/
│   ├── controller/ProductController.java
│   ├── service/ProductService.java
│   ├── repository/ProductRepository.java
│   ├── entity/Product.java
│   ├── dto/ProductDTO.java
│   └── exception/ProductException.java
├── order/
│   ├── controller/OrderController.java
│   ├── service/OrderService.java
│   ├── repository/OrderRepository.java
│   ├── entity/Order.java
│   ├── dto/OrderDTO.java
│   └── exception/OrderException.java
├── common/
│   ├── config/AppConfig.java
│   ├── exception/GlobalExceptionHandler.java
│   ├── dto/APIResponse.java
│   └── util/DateUtil.java
└── SpringBootApp.java
```

### Benefits
- Organized around business domains
- Easy to navigate related functionality
- Scales well with project growth
- Facilitates microservice extraction
- Team ownership by feature

### Drawbacks
- More directories to navigate initially
- Requires discipline to maintain boundaries
- Potential for code duplication across features

## Choosing Between Patterns

| Criteria | Layer-Based | Feature-Based |
|----------|-------------|---------------|
| **Team Size** | <5 developers | 5+ developers |
| **Project Scope** | Small/Medium | Large/Complex |
| **Code Reusability** | High across layers | Within feature scope |
| **Scalability** | Limited | Excellent |
| **Feature Isolation** | Low | High |
| **Microservice Extraction** | Difficult | Easy |

### Decision Tree

1. Is this a new microservice or monolith?
   - **Microservice** → Layer-based (simpler)
   - **Monolith** → Feature-based (more organized)

2. How many features/domains?
   - **1-3** → Layer-based
   - **4+** → Feature-based

3. How many developers?
   - **<5** → Layer-based
   - **5+** → Feature-based

## Package Organization

### Layer-Based Package Names

```java
package com.company.app.controller;
package com.company.app.service;
package com.company.app.repository;
```

### Feature-Based Package Names

```java
package com.company.app.product.controller;
package com.company.app.product.service;
package com.company.app.common.config;
```

### Shared Utilities

Always create a `common` or `shared` package for cross-cutting concerns:

```
common/
├── config/          → Spring configuration
├── exception/       → Global exceptions
├── dto/             → Shared DTOs (APIResponse)
├── util/            → Utility classes
├── annotation/      → Custom annotations
├── interceptor/     → HTTP interceptors
└── filter/          → Servlet filters
```

## Dependency Flow

**Always enforce this dependency direction**:

```
Controller
    ↓
Service
    ↓
Repository
    ↓
Entity
```

### Anti-Patterns

❌ Controller accessing Repository directly
❌ Service accessing Controller
❌ Repository business logic
❌ Entity containing business logic

### Correct Pattern

```java
// ✅ Controller → Service
@RestController
public class ProductController {
    private final ProductService service;  // Inject Service
}

// ✅ Service → Repository
@Service
public class ProductService {
    private final ProductRepository repository;  // Inject Repository
}

// ✅ Repository → Database
public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

## Handling Cross-Cutting Concerns

Use the **common** package for:

- **GlobalExceptionHandler** → Exception handling
- **RequestInterceptor** → Logging, tracing
- **AuthenticationFilter** → Security
- **CacheConfig** → Cache configuration
- **RestTemplateConfig** → HTTP client setup

Example location: `common/handler/GlobalExceptionHandler.java`
