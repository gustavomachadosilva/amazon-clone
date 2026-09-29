package com.mercatto.orders.api;

import com.mercatto.orders.service.BoughtTogetherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (anonymous GET, see {@code JwtAuthenticationFilter}) "Frequently bought together" bundle
 * for the product page (Card #224). It only exposes aggregate counts of at least two buyers, never
 * who bought what.
 */
@RestController
@RequiredArgsConstructor
public class BoughtTogetherController {

    static final int MAX_LIMIT = 10;

    private final BoughtTogetherService boughtTogetherService;

    @GetMapping("/api/orders/bought-together/{productId}")
    public BoughtTogetherService.BoughtTogether boughtTogether(@PathVariable Long productId,
                                                               @RequestParam(defaultValue = "2") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return boughtTogetherService.find(productId, limit);
    }
}
