package com.mercatto.cart.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.cart.service.CartService;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(10L, UserRole.BUYER);
    private static final AuthenticatedUser OTHER_USER = new AuthenticatedUser(99L, UserRole.BUYER);

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
    void getCartAsOwner_returns200() throws Exception {
        when(cartService.getCart(anyLong())).thenReturn(emptyCart(10L));

        mockMvc.perform(get("/api/cart/10").principal(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10));

        verify(cartService).getCart(10L);
    }

    @Test
    void getCartAsOtherUser_returns403() throws Exception {
        mockMvc.perform(get("/api/cart/10").principal(OTHER_USER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void addItemWithValidRequest_returns200() throws Exception {
        when(cartService.addItem(anyLong(), anyLong(), anyInt())).thenReturn(emptyCart(10L));

        CartController.AddItemRequest request = new CartController.AddItemRequest(1L, 2);

        mockMvc.perform(post("/api/cart/10/items")
                        .contentType("application/json")
                        .principal(OWNER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10));

        verify(cartService).addItem(10L, 1L, 2);
    }

    @Test
    void addItemAsOtherUser_returns403() throws Exception {
        CartController.AddItemRequest request = new CartController.AddItemRequest(1L, 2);

        mockMvc.perform(post("/api/cart/10/items")
                        .contentType("application/json")
                        .principal(OTHER_USER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
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
                        .principal(OWNER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void updateQuantityWhenItemNotInCart_returns404() throws Exception {
        when(cartService.updateQuantity(anyLong(), anyLong(), anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/cart/10/items/1")
                        .contentType("application/json")
                        .principal(OWNER)
                        .content(objectMapper.writeValueAsString(new CartController.UpdateQuantityRequest(3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateQuantityAsOtherUser_returns403() throws Exception {
        mockMvc.perform(put("/api/cart/10/items/1")
                        .contentType("application/json")
                        .principal(OTHER_USER)
                        .content(objectMapper.writeValueAsString(new CartController.UpdateQuantityRequest(3))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void removeItemWhenItemNotInCart_returns404() throws Exception {
        when(cartService.removeItem(anyLong(), anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/cart/10/items/1").principal(OWNER))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItemAsOtherUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/cart/10/items/1").principal(OTHER_USER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void saveForLaterWhenItemNotInCart_returns404() throws Exception {
        when(cartService.saveForLater(anyLong(), anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/cart/10/items/1/save-for-later").principal(OWNER))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveForLaterAsOtherUser_returns403() throws Exception {
        mockMvc.perform(post("/api/cart/10/items/1/save-for-later").principal(OTHER_USER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void moveToCartWhenItemNotSaved_returns404() throws Exception {
        when(cartService.moveToCart(anyLong(), anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/cart/10/items/1/move-to-cart").principal(OWNER))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveToCartAsOtherUser_returns403() throws Exception {
        mockMvc.perform(post("/api/cart/10/items/1/move-to-cart").principal(OTHER_USER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void clearCart_returns200() throws Exception {
        when(cartService.clear(anyLong())).thenReturn(emptyCart(10L));

        mockMvc.perform(delete("/api/cart/10").principal(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10));

        verify(cartService).clear(10L);
    }

    @Test
    void clearCartAsOtherUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/cart/10").principal(OTHER_USER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }
}
