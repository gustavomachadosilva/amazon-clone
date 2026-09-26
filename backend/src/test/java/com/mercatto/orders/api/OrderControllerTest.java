package com.mercatto.orders.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.OrderStatus;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import com.mercatto.orders.service.OrderAccessDeniedException;
import com.mercatto.orders.service.OrderAddressNotEditableException;
import com.mercatto.orders.service.InsufficientStockException;
import com.mercatto.orders.service.OrderNotFoundException;
import com.mercatto.orders.service.OrderPaymentNotRetryableException;
import com.mercatto.orders.service.OrderService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

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

    private static Order placedOrder() {
        Order order = Order.builder()
                .id(1L)
                .buyerId(10L)
                .idempotencyKey("secret-key")
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("39.80"))
                .address(testAddress())
                .shippingMethod(ShippingMethod.STANDARD)
                .paymentMethod(PaymentMethod.CARD)
                .createdAt(Instant.parse("2026-09-28T15:00:00Z"))
                .build();
        order.addItem(OrderItem.builder()
                .id(5L).productId(3L).sellerId(40L).quantity(2).unitPrice(new BigDecimal("19.90")).build());
        return order;
    }

    @Test
    void getByIdAsOwner_returnsOrderResponseDto() throws Exception {
        when(orderService.findById(1L)).thenReturn(Optional.of(placedOrder()));

        mockMvc.perform(get("/api/orders/1").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.buyerId").value(10))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$.totalAmount").value(39.80))
                .andExpect(jsonPath("$.address.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.address.street").value("1578 Union Street, Apt 92"))
                .andExpect(jsonPath("$.address.city").value("Seattle"))
                .andExpect(jsonPath("$.address.state").value("WA"))
                .andExpect(jsonPath("$.address.zip").value("98104"))
                .andExpect(jsonPath("$.shippingMethod").value("STANDARD"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.items[0].productId").value(3))
                .andExpect(jsonPath("$.items[0].sellerId").value(40))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19.90))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T15:00:00Z"))
                .andExpect(jsonPath("$.shippedAt").value(nullValue()))
                .andExpect(jsonPath("$.outForDeliveryAt").value(nullValue()))
                .andExpect(jsonPath("$.deliveredAt").value(nullValue()))
                // Monday 2026-09-28, STANDARD = 5 business days.
                .andExpect(jsonPath("$.estimatedDeliveryDate").value("2026-10-05"))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @Test
    void getByIdOfOrderPaidLaterThroughRetry_estimatesDeliveryFromThePayment() throws Exception {
        Order order = Order.builder()
                .id(1L)
                .buyerId(10L)
                .status(OrderStatus.PAID)
                .totalAmount(BigDecimal.TEN)
                .shippingMethod(ShippingMethod.STANDARD)
                // Placed (and declined) on Monday 2026-09-28, paid through a retry on Monday 2026-10-12.
                .createdAt(Instant.parse("2026-09-28T15:00:00Z"))
                .paidAt(Instant.parse("2026-10-12T15:00:00Z"))
                .build();
        when(orderService.findById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/1").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedDeliveryDate").value("2026-10-19"));
    }

    @Test
    void getByIdOfShippedOrder_includesShippedAt() throws Exception {
        Order order = placedOrder();
        order.advanceFulfillmentTo(FulfillmentStatus.SHIPPED, Instant.parse("2026-09-29T12:00:00Z"));
        when(orderService.findById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/1").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.shippedAt").value("2026-09-29T12:00:00Z"))
                .andExpect(jsonPath("$.outForDeliveryAt").value(nullValue()));
    }

    @Test
    void getByIdOfLegacyOrderWithoutAddressOrMethods_returnsNulls() throws Exception {
        Order order = Order.builder()
                .id(1L)
                .buyerId(10L)
                .status(OrderStatus.PAID)
                .totalAmount(BigDecimal.TEN)
                .createdAt(Instant.parse("2026-09-28T15:00:00Z"))
                .build();
        when(orderService.findById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/1").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(nullValue()))
                .andExpect(jsonPath("$.shippingMethod").value(nullValue()))
                .andExpect(jsonPath("$.paymentMethod").value(nullValue()))
                .andExpect(jsonPath("$.fulfillmentStatus").value("NOT_SHIPPED"))
                // Null shipping method is estimated like STANDARD.
                .andExpect(jsonPath("$.estimatedDeliveryDate").value("2026-10-05"));
    }

    @Test
    void listByBuyer_returnsArrayOfOrderResponseDtos() throws Exception {
        when(orderService.findByBuyer(10L)).thenReturn(List.of(placedOrder()));

        mockMvc.perform(get("/api/orders").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fulfillmentStatus").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$[0].estimatedDeliveryDate").value("2026-10-05"))
                .andExpect(jsonPath("$[0].items[0].sellerId").value(40))
                .andExpect(jsonPath("$[0].idempotencyKey").doesNotExist());

        verify(orderService).findByBuyer(10L);
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

    // --- PATCH /api/orders/{id}/address ----------------------------------------------------

    private static final String NEW_ADDRESS_JSON = """
            {"fullName": "Grace Hopper", "street": "200 Navy Way", "city": "Arlington",
             "state": "VA", "zip": "22202"}
            """;

    @Test
    void updateAddressAsOwner_returns200WithTheNewAddress() throws Exception {
        Order order = placedOrder();
        order.setAddress(ShippingAddress.builder()
                .fullName("Grace Hopper").street("200 Navy Way").city("Arlington").state("VA").zip("22202").build());
        when(orderService.updateShippingAddress(eq(1L), eq(10L), any(ShippingAddress.class))).thenReturn(order);

        mockMvc.perform(patch("/api/orders/1/address")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(NEW_ADDRESS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fulfillmentStatus").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$.address.fullName").value("Grace Hopper"))
                .andExpect(jsonPath("$.address.street").value("200 Navy Way"))
                .andExpect(jsonPath("$.address.city").value("Arlington"))
                .andExpect(jsonPath("$.address.state").value("VA"))
                .andExpect(jsonPath("$.address.zip").value("22202"))
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        ArgumentCaptor<ShippingAddress> addressCaptor = ArgumentCaptor.forClass(ShippingAddress.class);
        verify(orderService).updateShippingAddress(eq(1L), eq(10L), addressCaptor.capture());
        assertThat(addressCaptor.getValue().getFullName()).isEqualTo("Grace Hopper");
        assertThat(addressCaptor.getValue().getStreet()).isEqualTo("200 Navy Way");
        assertThat(addressCaptor.getValue().getCity()).isEqualTo("Arlington");
        assertThat(addressCaptor.getValue().getState()).isEqualTo("VA");
        assertThat(addressCaptor.getValue().getZip()).isEqualTo("22202");
    }

    static Stream<String> invalidAddressPayloads() {
        return Stream.of(
                // blank field
                """
                {"fullName": "", "street": "200 Navy Way", "city": "Arlington", "state": "VA", "zip": "22202"}
                """,
                // missing field
                """
                {"fullName": "Grace Hopper", "street": "200 Navy Way", "city": "Arlington", "state": "VA"}
                """,
                // empty object
                "{}");
    }

    @ParameterizedTest
    @MethodSource("invalidAddressPayloads")
    void updateAddressWithInvalidPayload_returns400(String payload) throws Exception {
        mockMvc.perform(patch("/api/orders/1/address")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void updateAddressWithoutBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/orders/1/address")
                        .contentType("application/json")
                        .principal(BUYER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void updateAddressAsOtherBuyer_returns403() throws Exception {
        when(orderService.updateShippingAddress(eq(1L), eq(20L), any(ShippingAddress.class)))
                .thenThrow(new OrderAccessDeniedException("User 20 does not own order 1"));

        mockMvc.perform(patch("/api/orders/1/address")
                        .contentType("application/json")
                        .principal(OTHER_BUYER)
                        .content(NEW_ADDRESS_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAddressWhenNotFound_returns404() throws Exception {
        when(orderService.updateShippingAddress(eq(1L), eq(10L), any(ShippingAddress.class)))
                .thenThrow(new OrderNotFoundException("Order not found: 1"));

        mockMvc.perform(patch("/api/orders/1/address")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(NEW_ADDRESS_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAddressOfShippedOrder_returns409() throws Exception {
        when(orderService.updateShippingAddress(eq(1L), eq(10L), any(ShippingAddress.class)))
                .thenThrow(new OrderAddressNotEditableException("Order already shipped"));

        mockMvc.perform(patch("/api/orders/1/address")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(NEW_ADDRESS_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Order already shipped"));
    }

    // --- POST /api/orders/{id}/payment ----------------------------------------------------

    @Test
    void retryPaymentAsOwner_returns200WithTheUpdatedOrder() throws Exception {
        Order order = placedOrder();
        order.setPaymentMethod(PaymentMethod.GIFT);
        when(orderService.retryPayment(1L, 10L, PaymentMethod.GIFT)).thenReturn(order);

        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content("{\"paymentMethod\": \"GIFT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("GIFT"))
                .andExpect(jsonPath("$.totalAmount").value(39.80))
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        ArgumentCaptor<Long> orderId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> buyerId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<PaymentMethod> method = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(orderService).retryPayment(orderId.capture(), buyerId.capture(), method.capture());
        assertThat(orderId.getValue()).isEqualTo(1L);
        assertThat(buyerId.getValue()).isEqualTo(10L);
        assertThat(method.getValue()).isEqualTo(PaymentMethod.GIFT);
    }

    @Test
    void retryPaymentDeclinedAgain_returns200WithFailedStatus() throws Exception {
        Order order = placedOrder();
        order.setStatus(OrderStatus.FAILED);
        when(orderService.retryPayment(1L, 10L, PaymentMethod.CARD)).thenReturn(order);

        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"paymentMethod\": null}", "{\"paymentMethod\": \"BITCOIN\"}"})
    void retryPaymentWithInvalidPayload_returns400(String payload) throws Exception {
        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void retryPaymentWithoutBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void retryPaymentAsOtherBuyer_returns403() throws Exception {
        when(orderService.retryPayment(1L, 20L, PaymentMethod.CARD))
                .thenThrow(new OrderAccessDeniedException("User 20 does not own order 1"));

        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(OTHER_BUYER)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void retryPaymentWhenNotFound_returns404() throws Exception {
        when(orderService.retryPayment(1L, 10L, PaymentMethod.CARD))
                .thenThrow(new OrderNotFoundException("Order not found: 1"));

        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void retryPaymentOfAnOrderThatIsNotFailed_returns409() throws Exception {
        when(orderService.retryPayment(1L, 10L, PaymentMethod.CARD))
                .thenThrow(new OrderPaymentNotRetryableException(
                        "Only FAILED orders can have their payment retried; order 1 is PAID"));

        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only FAILED orders can have their payment retried; order 1 is PAID"));
    }

    @Test
    void retryPaymentWhenStockRanOut_returns409() throws Exception {
        when(orderService.retryPayment(1L, 10L, PaymentMethod.CARD))
                .thenThrow(new InsufficientStockException("Insufficient stock for product 3: requested 2, available 1"));

        mockMvc.perform(post("/api/orders/1/payment")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content("{\"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for product 3: requested 2, available 1"));
    }
}
