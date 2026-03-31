package com.company.app.product.controller;

import com.company.app.product.dto.CreateProductRequest;
import com.company.app.product.dto.ProductResponse;
import com.company.app.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Product CRUD operations.
 *
 * Responsibilities:
 * - HTTP routing and request/response mapping
 * - Input validation via @Valid
 * - Delegating all business logic to ProductService
 *
 * Uses Java Records for request/response DTOs (immutable, concise).
 * Error responses follow RFC 7807 ProblemDetail via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService service;

    // ---------- CREATE ----------

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        log.info("Creating product: {}", request.name());
        ProductResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ---------- READ ----------

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort) {
        var pageable = PageRequest.of(page, size, Sort.by(sort));
        return ResponseEntity.ok(service.getAll(pageable));
    }

    // ---------- UPDATE ----------

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request) {
        log.info("Updating product {}: {}", id, request.name());
        return ResponseEntity.ok(service.update(id, request));
    }

    // ---------- DELETE ----------

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting product: {}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
