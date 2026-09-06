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
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    private static final AuthenticatedUser SELLER = new AuthenticatedUser(1L, UserRole.SELLER);
    private static final AuthenticatedUser BUYER = new AuthenticatedUser(1L, UserRole.BUYER);

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
}
