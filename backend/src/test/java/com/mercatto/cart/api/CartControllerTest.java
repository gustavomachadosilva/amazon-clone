package com.mercatto.cart.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.cart.service.CartService;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @MockBean
    private TokenService tokenService;

    private static CartService.CartView emptyCart(Long userId) {
        return new CartService.CartView(userId, List.of(), List.of(), 0, BigDecimal.ZERO);
    }

    @Test
    void addItemWithValidRequest_returns200() throws Exception {
        when(cartService.addItem(anyLong(), anyLong(), anyInt())).thenReturn(emptyCart(10L));

        CartController.AddItemRequest request = new CartController.AddItemRequest(1L, 2);

        mockMvc.perform(post("/api/cart/10/items")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10));

        verify(cartService).addItem(10L, 1L, 2);
    }

    static Stream<CartController.AddItemRequest> invalidAddItemRequests() {
        return Stream.of(
                // productId null
                new CartController.AddItemRequest(null, 2),
                // quantity <= 0
                new CartController.AddItemRequest(1L, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidAddItemRequests")
    void addItemWithInvalidRequest_returns400(CartController.AddItemRequest request) throws Exception {
        mockMvc.perform(post("/api/cart/10/items")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void updateQuantityWhenItemNotInCart_returns404() throws Exception {
        when(cartService.updateQuantity(anyLong(), anyLong(), anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/cart/10/items/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CartController.UpdateQuantityRequest(3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItemWhenItemNotInCart_returns404() throws Exception {
        when(cartService.removeItem(anyLong(), anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/cart/10/items/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_returns200() throws Exception {
        when(cartService.clear(anyLong())).thenReturn(emptyCart(10L));

        mockMvc.perform(delete("/api/cart/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10));

        verify(cartService).clear(10L);
    }
}
