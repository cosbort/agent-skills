package com.company.app.product.service;

import com.company.app.exception.DuplicateResourceException;
import com.company.app.exception.ResourceNotFoundException;
import com.company.app.product.dto.CreateProductRequest;
import com.company.app.product.dto.ProductResponse;
import com.company.app.product.entity.Product;
import com.company.app.product.mapper.ProductMapper;
import com.company.app.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for Product business logic.
 *
 * All business rules, validations, and data transformations live here.
 * The controller delegates to this service; the repository is accessed
 * only through this layer.
 *
 * Caching: individual products by ID, evicted on create/update/delete.
 * Transactions: read-only for queries, read-write for mutations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    // ---------- READ ----------

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getById(Long id) {
        log.debug("Fetching product by ID: {}", id);
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public Page<ProductResponse> getAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toResponse);
    }

    // ---------- CREATE ----------

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse create(CreateProductRequest request) {
        log.info("Creating product: {}", request.name());

        if (repository.existsByName(request.name())) {
            throw new DuplicateResourceException("Product", "name", request.name());
        }

        Product entity = mapper.toEntity(request);
        Product saved = repository.save(entity);

        log.info("Product created with ID: {}", saved.getId());
        return mapper.toResponse(saved);
    }

    // ---------- UPDATE ----------

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse update(Long id, CreateProductRequest request) {
        log.info("Updating product {}: {}", id, request.name());

        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // Check name uniqueness only if it changed
        if (!product.getName().equals(request.name())
                && repository.existsByName(request.name())) {
            throw new DuplicateResourceException("Product", "name", request.name());
        }

        product.setName(request.name());
        product.setPrice(request.price());
        product.setDescription(request.description());

        return mapper.toResponse(repository.save(product));
    }

    // ---------- DELETE ----------

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void delete(Long id) {
        log.info("Deleting product: {}", id);

        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }

        repository.deleteById(id);
    }
}
