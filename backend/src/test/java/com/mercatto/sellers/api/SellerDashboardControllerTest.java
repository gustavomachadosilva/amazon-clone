package com.mercatto.sellers.api;

import com.mercatto.catalog.domain.Product;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.sellers.service.SellerDashboardService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class SellerDashboardControllerTest {

    private static final AuthenticatedUser SELLER = new AuthenticatedUser(10L, UserRole.SELLER);
    private static final AuthenticatedUser OTHER_SELLER = new AuthenticatedUser(99L, UserRole.SELLER);
    private static final AuthenticatedUser BUYER = new AuthenticatedUser(10L, UserRole.BUYER);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SellerDashboardService sellerDashboardService;

    @MockBean
    private TokenService tokenService;

    @Test
    void inventoryAsOwnSeller_returns200() throws Exception {
        Product product = Product.builder().id(1L).name("Widget").price(BigDecimal.TEN).stockQuantity(5)
                .category("tools").sellerId(10L).build();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(sellerDashboardService.getInventory(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/sellers/10/products").principal(SELLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(sellerDashboardService).getInventory(anyLong(), any(Pageable.class));
    }

    @Test
    void inventoryAsOtherSeller_returns403() throws Exception {
        mockMvc.perform(get("/api/sellers/10/products").principal(OTHER_SELLER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sellerDashboardService);
    }

    @Test
    void inventoryAsBuyer_returns403() throws Exception {
        mockMvc.perform(get("/api/sellers/10/products").principal(BUYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sellerDashboardService);
    }

    @Test
    void receivedOrdersAsOwnSeller_returns200() throws Exception {
        Order order = Order.builder().id(1L).buyerId(20L).status(OrderStatus.PAID)
                .totalAmount(BigDecimal.TEN).build();
        when(sellerDashboardService.getReceivedOrders(10L)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/sellers/10/orders").principal(SELLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(sellerDashboardService).getReceivedOrders(10L);
    }

    @Test
    void receivedOrdersAsOtherSeller_returns403() throws Exception {
        mockMvc.perform(get("/api/sellers/10/orders").principal(OTHER_SELLER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sellerDashboardService);
    }

    @Test
    void receivedOrdersAsBuyer_returns403() throws Exception {
        mockMvc.perform(get("/api/sellers/10/orders").principal(BUYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sellerDashboardService);
    }
}
