package com.mercatto.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private static ProductController.CreateProductRequest validRequest() {
        return new ProductController.CreateProductRequest(
                "Widget", "A useful widget", BigDecimal.TEN, 10, "tools", "http://example.com/img.png", 1L);
    }

    @Test
    void createWithValidRequest_returns200AndCallsService() throws Exception {
        Product saved = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(10)
                .category("tools").sellerId(1L).build();
        when(productService.create(any(Product.class))).thenReturn(saved);

        mockMvc.perform(post("/api/catalog/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(productService).create(any(Product.class));
    }

    static Stream<ProductController.CreateProductRequest> invalidRequests() {
        return Stream.of(
                // name blank
                new ProductController.CreateProductRequest(
                        "", "desc", BigDecimal.TEN, 10, "tools", "http://x", 1L),
                // price null
                new ProductController.CreateProductRequest(
                        "Widget", "desc", null, 10, "tools", "http://x", 1L),
                // price <= 0
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.ZERO, 10, "tools", "http://x", 1L),
                // stockQuantity null
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, null, "tools", "http://x", 1L),
                // stockQuantity negative
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, -1, "tools", "http://x", 1L),
                // category blank
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, 10, "", "http://x", 1L),
                // sellerId null
                new ProductController.CreateProductRequest(
                        "Widget", "desc", BigDecimal.TEN, 10, "tools", "http://x", null)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void createWithInvalidRequest_returns400(ProductController.CreateProductRequest request) throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }
}
