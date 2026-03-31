package com.company.app.product.service;

import com.company.app.exception.DuplicateResourceException;
import com.company.app.exception.ResourceNotFoundException;
import com.company.app.product.dto.CreateProductRequest;
import com.company.app.product.dto.ProductResponse;
import com.company.app.product.entity.Product;
import com.company.app.product.mapper.ProductMapper;
import com.company.app.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// =====================================================================
// UNIT TESTS — Service layer with mocked dependencies
// =====================================================================

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
                .description("A powerful laptop")
                .createdAt(Instant.now())
                .build();

        response = new ProductResponse(
                1L, "Laptop", BigDecimal.valueOf(999.99),
                "A powerful laptop", Instant.now());
    }

    // --- GET BY ID ---

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void shouldReturnProduct_whenFound() {
            when(repository.findById(1L)).thenReturn(Optional.of(product));
            when(mapper.toResponse(product)).thenReturn(response);

            ProductResponse result = service.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Laptop");
            assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
            verify(repository).findById(1L);
            verify(mapper).toResponse(product);
        }

        @Test
        void shouldThrow_whenNotFound() {
            when(repository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 1");
        }
    }

    // --- CREATE ---

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void shouldCreateProduct_whenNameIsUnique() {
            var request = new CreateProductRequest("Phone", BigDecimal.valueOf(599), "A phone");

            when(repository.existsByName("Phone")).thenReturn(false);
            when(mapper.toEntity(request)).thenReturn(product);
            when(repository.save(any(Product.class))).thenReturn(product);
            when(mapper.toResponse(product)).thenReturn(response);

            ProductResponse result = service.create(request);

            assertThat(result).isNotNull();
            verify(repository).existsByName("Phone");
            verify(repository).save(any(Product.class));
        }

        @Test
        void shouldThrow_whenNameAlreadyExists() {
            var request = new CreateProductRequest("Existing", BigDecimal.valueOf(100), null);

            when(repository.existsByName("Existing")).thenReturn(true);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists");

            verify(repository, never()).save(any());
        }
    }

    // --- UPDATE ---

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void shouldUpdateProduct_whenExists() {
            var request = new CreateProductRequest("New Name", BigDecimal.valueOf(150), "Updated");

            when(repository.findById(1L)).thenReturn(Optional.of(product));
            when(repository.existsByName("New Name")).thenReturn(false);
            when(repository.save(any(Product.class))).thenReturn(product);
            when(mapper.toResponse(any(Product.class))).thenReturn(
                    new ProductResponse(1L, "New Name", BigDecimal.valueOf(150),
                            "Updated", Instant.now()));

            ProductResponse result = service.update(1L, request);

            assertThat(result.name()).isEqualTo("New Name");
            verify(repository).save(any(Product.class));
        }

        @Test
        void shouldThrow_whenProductToUpdateNotFound() {
            var request = new CreateProductRequest("Name", BigDecimal.ONE, null);

            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldThrow_whenNewNameConflicts() {
            var request = new CreateProductRequest("Conflict", BigDecimal.ONE, null);

            when(repository.findById(1L)).thenReturn(Optional.of(product));
            when(repository.existsByName("Conflict")).thenReturn(true);

            assertThatThrownBy(() -> service.update(1L, request))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(repository, never()).save(any());
        }

        @Test
        void shouldSkipUniquenessCheck_whenNameUnchanged() {
            var request = new CreateProductRequest("Laptop", BigDecimal.valueOf(1200), "Same name");

            when(repository.findById(1L)).thenReturn(Optional.of(product));
            when(repository.save(any(Product.class))).thenReturn(product);
            when(mapper.toResponse(any(Product.class))).thenReturn(response);

            service.update(1L, request);

            // existsByName should NOT be called when name didn't change
            verify(repository, never()).existsByName(anyString());
            verify(repository).save(any(Product.class));
        }
    }

    // --- DELETE ---

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void shouldDeleteProduct_whenExists() {
            when(repository.existsById(1L)).thenReturn(true);

            service.delete(1L);

            verify(repository).deleteById(1L);
        }

        @Test
        void shouldThrow_whenProductToDeleteNotFound() {
            when(repository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).deleteById(any());
        }
    }
}

// =====================================================================
// INTEGRATION TEST — Full stack with Testcontainers (separate file)
// =====================================================================
//
// Place this in: src/test/java/.../ProductControllerIT.java
//
// @SpringBootTest
// @AutoConfigureMockMvc
// @Testcontainers
// class ProductControllerIT {
//
//     @Container
//     @ServiceConnection
//     static PostgreSQLContainer<?> postgres =
//         new PostgreSQLContainer<>("postgres:16-alpine");
//
//     @Autowired private MockMvc mockMvc;
//     @Autowired private ProductRepository repository;
//     @Autowired private ObjectMapper objectMapper;
//
//     @BeforeEach
//     void setUp() {
//         repository.deleteAll();
//     }
//
//     @Test
//     void shouldCreateProduct() throws Exception {
//         var request = new CreateProductRequest("Widget", BigDecimal.valueOf(29.99), "A widget");
//
//         mockMvc.perform(post("/api/v1/products")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(request)))
//             .andExpect(status().isCreated())
//             .andExpect(jsonPath("$.name").value("Widget"))
//             .andExpect(jsonPath("$.id").exists());
//
//         assertThat(repository.count()).isEqualTo(1);
//     }
//
//     @Test
//     void shouldReturn404_whenProductNotFound() throws Exception {
//         mockMvc.perform(get("/api/v1/products/999"))
//             .andExpect(status().isNotFound())
//             .andExpect(jsonPath("$.title").value("ResourceNotFoundException"));
//     }
// }
