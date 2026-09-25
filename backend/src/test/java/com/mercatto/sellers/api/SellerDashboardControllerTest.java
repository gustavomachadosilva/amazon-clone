package com.mercatto.sellers.api;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.InvalidFulfillmentTransitionException;
import com.mercatto.orders.service.OrderAccessDeniedException;
import com.mercatto.orders.service.OrderNotFoundException;
import com.mercatto.orders.service.OrderStatus;
import com.mercatto.sellers.service.SellerDashboardService;
import com.mercatto.sellers.service.SellerDashboardService.SellerOrderItemView;
import com.mercatto.sellers.service.SellerDashboardService.SellerOrderView;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        ProductService.ProductSummary product = new ProductService.ProductSummary(
                1L, "Widget", null, BigDecimal.TEN, 5, "tools", null, null, null, null, null, 10L, null);
        Page<ProductService.ProductSummary> page = new PageImpl<>(List.of(product));
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
        SellerOrderView order = new SellerOrderView(1L, 20L, OrderStatus.PAID, FulfillmentStatus.NOT_SHIPPED, Instant.now(),
                List.of(new SellerOrderItemView(5L, 2, BigDecimal.TEN)), BigDecimal.valueOf(20));
        when(sellerDashboardService.getReceivedOrders(10L)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/sellers/10/orders").principal(SELLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].fulfillmentStatus").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$[0].items[0].productId").value(5));

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

    @Test
    void advanceFulfillmentAsOwnSeller_returns200WithUpdatedOrder() throws Exception {
        SellerOrderView order = new SellerOrderView(1L, 20L, OrderStatus.PAID, FulfillmentStatus.SHIPPED, Instant.now(),
                List.of(new SellerOrderItemView(5L, 2, BigDecimal.TEN)), BigDecimal.valueOf(20));
        when(sellerDashboardService.advanceFulfillment(10L, 1L, FulfillmentStatus.SHIPPED)).thenReturn(order);

        mockMvc.perform(advanceRequest("{\"status\":\"SHIPPED\"}").principal(SELLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.fulfillmentStatus").value("SHIPPED"));

        verify(sellerDashboardService).advanceFulfillment(10L, 1L, FulfillmentStatus.SHIPPED);
    }

    @Test
    void advanceFulfillmentAsOtherSeller_returns403() throws Exception {
        mockMvc.perform(advanceRequest("{\"status\":\"SHIPPED\"}").principal(OTHER_SELLER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sellerDashboardService);
    }

    @Test
    void advanceFulfillmentAsBuyer_returns403() throws Exception {
        mockMvc.perform(advanceRequest("{\"status\":\"SHIPPED\"}").principal(BUYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sellerDashboardService);
    }

    @Test
    void advanceFulfillmentWhenSellerHasNoItemInOrder_returns403() throws Exception {
        when(sellerDashboardService.advanceFulfillment(10L, 1L, FulfillmentStatus.SHIPPED))
                .thenThrow(new OrderAccessDeniedException("Seller has no items in this order"));

        mockMvc.perform(advanceRequest("{\"status\":\"SHIPPED\"}").principal(SELLER))
                .andExpect(status().isForbidden());
    }

    @Test
    void advanceFulfillmentSkippingAStep_returns409() throws Exception {
        when(sellerDashboardService.advanceFulfillment(10L, 1L, FulfillmentStatus.DELIVERED))
                .thenThrow(new InvalidFulfillmentTransitionException("Cannot move from NOT_SHIPPED to DELIVERED"));

        mockMvc.perform(advanceRequest("{\"status\":\"DELIVERED\"}").principal(SELLER))
                .andExpect(status().isConflict());
    }

    @Test
    void advanceFulfillmentForUnknownOrder_returns404() throws Exception {
        when(sellerDashboardService.advanceFulfillment(10L, 1L, FulfillmentStatus.SHIPPED))
                .thenThrow(new OrderNotFoundException("Order not found: 1"));

        mockMvc.perform(advanceRequest("{\"status\":\"SHIPPED\"}").principal(SELLER))
                .andExpect(status().isNotFound());
    }

    @Test
    void advanceFulfillmentWithoutStatus_returns400() throws Exception {
        mockMvc.perform(advanceRequest("{}").principal(SELLER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sellerDashboardService);
    }

    @Test
    void advanceFulfillmentWithUnknownStatus_returns400() throws Exception {
        mockMvc.perform(advanceRequest("{\"status\":\"BOGUS\"}").principal(SELLER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sellerDashboardService);
    }

    private static MockHttpServletRequestBuilder advanceRequest(String body) {
        return post("/api/sellers/10/orders/1/fulfillment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
