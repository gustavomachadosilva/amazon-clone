package com.mercatto.orders.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import com.mercatto.orders.service.OrderService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final AuthenticatedUser BUYER = new AuthenticatedUser(10L, UserRole.BUYER);
    private static final AuthenticatedUser SELLER = new AuthenticatedUser(10L, UserRole.SELLER);
    private static final AuthenticatedUser OTHER_BUYER = new AuthenticatedUser(20L, UserRole.BUYER);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private TokenService tokenService;

    private static ShippingAddress testAddress() {
        return ShippingAddress.builder()
                .fullName("Ada Lovelace")
                .street("1578 Union Street, Apt 92")
                .city("Seattle")
                .state("WA")
                .zip("98104")
                .build();
    }

    @Test
    void checkoutWithValidRequest_returns200() throws Exception {
        Order order = Order.builder()
                .id(1L)
                .buyerId(10L)
                .status(OrderStatus.PAID)
                .totalAmount(BigDecimal.TEN)
                .address(testAddress())
                .shippingMethod(ShippingMethod.STANDARD)
                .paymentMethod(PaymentMethod.CARD)
                .build();
        when(orderService.checkout(anyLong(), anyList(), any(), any(), any(), any())).thenReturn(order);

        OrderController.CheckoutRequest request = new OrderController.CheckoutRequest(
                List.of(new OrderService.CheckoutItem(1L, 2)), testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.address.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.address.street").value("1578 Union Street, Apt 92"))
                .andExpect(jsonPath("$.address.city").value("Seattle"))
                .andExpect(jsonPath("$.address.state").value("WA"))
                .andExpect(jsonPath("$.address.zip").value("98104"))
                .andExpect(jsonPath("$.shippingMethod").value("STANDARD"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"));

        ArgumentCaptor<ShippingAddress> addressCaptor = ArgumentCaptor.forClass(ShippingAddress.class);
        verify(orderService).checkout(
                eq(10L), anyList(), any(), addressCaptor.capture(), eq(ShippingMethod.STANDARD), eq(PaymentMethod.CARD));
        assertThat(addressCaptor.getValue().getFullName()).isEqualTo("Ada Lovelace");
        assertThat(addressCaptor.getValue().getStreet()).isEqualTo("1578 Union Street, Apt 92");
        assertThat(addressCaptor.getValue().getCity()).isEqualTo("Seattle");
        assertThat(addressCaptor.getValue().getState()).isEqualTo("WA");
        assertThat(addressCaptor.getValue().getZip()).isEqualTo("98104");
    }

    @Test
    void checkoutAsSeller_returns403() throws Exception {
        OrderController.CheckoutRequest request = new OrderController.CheckoutRequest(
                List.of(new OrderService.CheckoutItem(1L, 2)), testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .principal(SELLER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }

    static Stream<OrderController.CheckoutRequest> invalidRequests() {
        return Stream.of(
                // items empty
                new OrderController.CheckoutRequest(List.of(), testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD),
                // item quantity <= 0
                new OrderController.CheckoutRequest(
                        List.of(new OrderService.CheckoutItem(1L, 0)), testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD),
                // item productId null
                new OrderController.CheckoutRequest(
                        List.of(new OrderService.CheckoutItem(null, 2)), testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD),
                // address missing entirely
                new OrderController.CheckoutRequest(
                        List.of(new OrderService.CheckoutItem(1L, 2)), null, ShippingMethod.STANDARD, PaymentMethod.CARD),
                // address field blank
                new OrderController.CheckoutRequest(
                        List.of(new OrderService.CheckoutItem(1L, 2)),
                        ShippingAddress.builder().fullName("").street("Street").city("City").state("ST").zip("00000").build(),
                        ShippingMethod.STANDARD,
                        PaymentMethod.CARD),
                // shippingMethod missing
                new OrderController.CheckoutRequest(
                        List.of(new OrderService.CheckoutItem(1L, 2)), testAddress(), null, PaymentMethod.CARD),
                // paymentMethod missing
                new OrderController.CheckoutRequest(
                        List.of(new OrderService.CheckoutItem(1L, 2)), testAddress(), ShippingMethod.STANDARD, null)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void checkoutWithInvalidRequest_returns400(OrderController.CheckoutRequest request) throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void getByIdAsOwner_returns200() throws Exception {
        Order order = Order.builder().id(1L).buyerId(10L).status(OrderStatus.PAID).totalAmount(BigDecimal.TEN).build();
        when(orderService.findById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/1").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getByIdAsOtherBuyer_returns403() throws Exception {
        Order order = Order.builder().id(1L).buyerId(10L).status(OrderStatus.PAID).totalAmount(BigDecimal.TEN).build();
        when(orderService.findById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/1").principal(OTHER_BUYER))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByIdWhenNotFound_returns404() throws Exception {
        when(orderService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/1").principal(BUYER))
                .andExpect(status().isNotFound());
    }
}
