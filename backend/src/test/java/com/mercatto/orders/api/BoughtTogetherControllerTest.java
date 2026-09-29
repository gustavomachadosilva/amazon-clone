package com.mercatto.orders.api;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.RelatedReason;
import com.mercatto.orders.service.BoughtTogetherService;
import com.mercatto.orders.service.BoughtTogetherService.BoughtTogether;
import com.mercatto.orders.service.BoughtTogetherService.BoughtTogetherSource;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoughtTogetherController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoughtTogetherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BoughtTogetherService boughtTogetherService;

    @MockBean
    private TokenService tokenService;

    private static ProductService.ProductView productView(long id) {
        return new ProductService.ProductView(id, "Widget", "A useful widget", BigDecimal.TEN, 10, "tools",
                "http://example.com/img.png", "Acme", 12, "MDL-1", BigDecimal.valueOf(15),
                1L, Instant.parse("2026-01-01T00:00:00Z"), 4.5, 3L);
    }

    @Test
    void returnsCoPurchasedItemsWithTheBuyerCountAndADefaultLimitOfTwo() throws Exception {
        when(boughtTogetherService.find(1L, 2)).thenReturn(new BoughtTogether(BoughtTogetherSource.CO_PURCHASE,
                List.of(new BoughtTogetherService.Item(productView(2L), 3, null))));

        mockMvc.perform(get("/api/orders/bought-together/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("CO_PURCHASE"))
                .andExpect(jsonPath("$.items[0].product.id").value(2))
                .andExpect(jsonPath("$.items[0].timesBoughtTogether").value(3))
                .andExpect(jsonPath("$.items[0].primaryReason").doesNotExist());
    }

    @Test
    void returnsSimilarItemsWithTheirReason() throws Exception {
        when(boughtTogetherService.find(1L, 4)).thenReturn(new BoughtTogether(BoughtTogetherSource.SIMILAR,
                List.of(new BoughtTogetherService.Item(productView(5L), null, RelatedReason.SAME_BRAND))));

        mockMvc.perform(get("/api/orders/bought-together/1").param("limit", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("SIMILAR"))
                .andExpect(jsonPath("$.items[0].product.id").value(5))
                .andExpect(jsonPath("$.items[0].primaryReason").value("SAME_BRAND"));

        verify(boughtTogetherService).find(1L, 4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "11", "abc"})
    void limitOutOfRange_returns400(String limit) throws Exception {
        mockMvc.perform(get("/api/orders/bought-together/1").param("limit", limit))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(boughtTogetherService);
    }

    @Test
    void unknownProduct_returns404() throws Exception {
        when(boughtTogetherService.find(99L, 2)).thenThrow(new ProductNotFoundException("Product not found: 99"));

        mockMvc.perform(get("/api/orders/bought-together/99"))
                .andExpect(status().isNotFound());
    }
}
