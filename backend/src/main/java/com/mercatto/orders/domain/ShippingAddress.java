package com.mercatto.orders.domain;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Snapshot of the buyer's shipping address at checkout time, embedded
 * directly on {@link Order}. The same type is reused both for JPA
 * persistence (via {@code @Embedded} on {@code Order}) and for request
 * validation (via {@code @Valid} on {@code OrderController.CheckoutRequest}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddress {

    @NotBlank
    private String fullName;

    @NotBlank
    private String street;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String zip;
}
