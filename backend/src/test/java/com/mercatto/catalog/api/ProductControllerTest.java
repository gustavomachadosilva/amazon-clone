package com.mercatto.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    private static final AuthenticatedUser SELLER = new AuthenticatedUser(1L, UserRole.SELLER);
    private static final AuthenticatedUser BUYER = new AuthenticatedUser(1L, UserRole.BUYER);
    private static final AuthenticatedUser OTHER_SELLER = new AuthenticatedUser(2L, UserRole.SELLER);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private TokenService tokenService;

    private static ProductController.CreateProductRequest validRequest() {
        return new ProductController.CreateProductRequest(
                "Widget", "A useful widget", BigDecimal.TEN, 10, "tools", "http://example.com/img.png");
    }

    private static ProductController.UpdateProductRequest validUpdateRequest() {
        return new ProductController.UpdateProductRequest(
                "Widget v2", "An updated widget", BigDecimal.valueOf(20), 5, "tools", "http://example.com/img2.png");
    }

    @Test
    void createWithValidRequest_returns200AndCallsService() throws Exception {
        Product saved = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(10)
                .category("tools").sellerId(1L).build();
        when(productService.create(any(Product.class))).thenReturn(saved);

        mockMvc.perform(post("/api/catalog/products")
                        .contentType("application/json")
                        .principal(SELLER)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(productService).create(any(Product.class));
    }

    @Test
    void createAsBuyer_returns403() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    static Stream<ProductController.CreateProductRequest> invalidRequests() {
        return Stream.of(
                // name blank
                new ProductController.CreateProductRequest(
                        "", "desc", BigDecimal.TEN, 10, "tools", "http://x"),
                // price null
                new ProductController.CreateProductRequest(
                        "Widget", "desc", null, 10, "tools", "http://x"),
                // price <= 0
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.ZERO, 10, "tools", "http://x"),
                // stockQuantity null
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, null, "tools", "http://x"),
                // stockQuantity negative
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, -1, "tools", "http://x"),
                // category blank
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, 10, "", "http://x")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void createWithInvalidRequest_returns400(ProductController.CreateProductRequest request) throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .contentType("application/json")
                        .principal(SELLER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void updateAsOwner_returns200AndCallsService() throws Exception {
        Product existing = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(10)
                .category("tools").sellerId(1L).build();
        Product updated = Product.builder().id(1L).name("Widget v2").price(BigDecimal.valueOf(20)).stockQuantity(5)
                .category("tools").sellerId(1L).build();
        when(productService.findById(1L)).thenReturn(Optional.of(existing));
        when(productService.update(eq(1L), any(Product.class))).thenReturn(updated);

        mockMvc.perform(put("/api/catalog/products/1")
                        .contentType("application/json")
                        .principal(SELLER)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Widget v2"));

        verify(productService).update(eq(1L), any(Product.class));
    }

    @Test
    void updateAsNonOwner_returns403() throws Exception {
        Product existing = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(10)
                .category("tools").sellerId(1L).build();
        when(productService.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(put("/api/catalog/products/1")
                        .contentType("application/json")
                        .principal(OTHER_SELLER)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isForbidden());

        verify(productService, never()).update(any(), any());
    }

    @Test
    void updateNonExistentProduct_returns404() throws Exception {
        when(productService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/catalog/products/1")
                        .contentType("application/json")
                        .principal(SELLER)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isNotFound());

        verify(productService, never()).update(any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void updateWithInvalidRequest_returns400(ProductController.CreateProductRequest invalidCreateRequest) throws Exception {
        ProductController.UpdateProductRequest request = new ProductController.UpdateProductRequest(
                invalidCreateRequest.name(), invalidCreateRequest.description(), invalidCreateRequest.price(),
                invalidCreateRequest.stockQuantity(), invalidCreateRequest.category(), invalidCreateRequest.imageUrl());

        mockMvc.perform(put("/api/catalog/products/1")
                        .contentType("application/json")
                        .principal(SELLER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void deleteAsOwner_returns204() throws Exception {
        Product existing = Product.builder().id(1L).sellerId(1L).build();
        when(productService.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/catalog/products/1").principal(SELLER))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }

    @Test
    void deleteAsNonOwner_returns403() throws Exception {
        Product existing = Product.builder().id(1L).sellerId(1L).build();
        when(productService.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/catalog/products/1").principal(OTHER_SELLER))
                .andExpect(status().isForbidden());

        verify(productService, never()).delete(any());
    }

    @Test
    void deleteNonExistentProduct_returns404() throws Exception {
        when(productService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/catalog/products/1").principal(SELLER))
                .andExpect(status().isNotFound());

        verify(productService, never()).delete(any());
    }
}
