package com.mercatto.config;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.orders.service.InsufficientStockException;
import com.mercatto.orders.service.InvalidFulfillmentTransitionException;
import com.mercatto.orders.service.OrderAccessDeniedException;
import com.mercatto.orders.service.OrderNotFoundException;
import com.mercatto.reviews.service.InvalidReviewMediaException;
import com.mercatto.reviews.service.ReviewMediaNotFoundException;
import com.mercatto.users.service.EmailAlreadyExistsException;
import com.mercatto.users.service.ForbiddenRoleException;
import com.mercatto.users.service.UserNotFoundException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Test-only controller used exclusively by {@link GlobalExceptionHandlerTest} to trigger each
 * exception type handled by {@link GlobalExceptionHandler} in isolation, independent of any
 * module's business setup. Lives under src/test so it is never packaged with the application.
 */
@RestController
@RequestMapping("/test")
public class ThrowingTestController {

    @GetMapping("/illegal-argument")
    public String illegalArgument() {
        throw new IllegalArgumentException("bad argument");
    }

    @GetMapping("/illegal-state")
    public String illegalState() {
        throw new IllegalStateException("bad state");
    }

    @GetMapping("/insufficient-stock")
    public String insufficientStock() {
        throw new InsufficientStockException("not enough stock");
    }

    @GetMapping("/order-not-found")
    public String orderNotFound() {
        throw new OrderNotFoundException("Order not found: 1");
    }

    @GetMapping("/order-access-denied")
    public String orderAccessDenied() {
        throw new OrderAccessDeniedException("Seller 2 has no items in order 1");
    }

    @GetMapping("/invalid-fulfillment-transition")
    public String invalidFulfillmentTransition() {
        throw new InvalidFulfillmentTransitionException("Cannot advance order 1 from NOT_SHIPPED to DELIVERED");
    }

    @GetMapping("/email-already-exists")
    public String emailAlreadyExists() {
        throw new EmailAlreadyExistsException("email already registered");
    }

    @GetMapping("/forbidden-role")
    public String forbiddenRole() {
        throw new ForbiddenRoleException("Apenas vendedores podem criar produtos");
    }

    @GetMapping("/data-integrity-violation")
    public String dataIntegrityViolation() {
        throw new DataIntegrityViolationException("unique constraint violated");
    }

    @GetMapping("/unexpected")
    public String unexpected() {
        throw new RuntimeException("boom - sensitive internal detail");
    }

    @GetMapping("/product-not-found")
    public String productNotFound() {
        throw new ProductNotFoundException("Product not found: 1");
    }

    @GetMapping("/user-not-found")
    public String userNotFound() {
        throw new UserNotFoundException("Usuário não encontrado: 1");
    }

    @GetMapping("/invalid-review-media")
    public String invalidReviewMedia() {
        throw new InvalidReviewMediaException("Only JPEG, PNG, WebP, MP4 or WebM files are allowed");
    }

    @GetMapping("/review-media-not-found")
    public String reviewMediaNotFound() {
        throw new ReviewMediaNotFoundException("Review media not found: 1");
    }

    @GetMapping("/max-upload-size-exceeded")
    public String maxUploadSizeExceeded() {
        throw new MaxUploadSizeExceededException(50L * 1024 * 1024);
    }

    @GetMapping("/illegal-argument-no-message")
    public String illegalArgumentNoMessage() {
        throw new IllegalArgumentException();
    }

    @GetMapping("/illegal-state-no-message")
    public String illegalStateNoMessage() {
        throw new IllegalStateException();
    }

    @PostMapping("/validated")
    public String validated(@Validated @RequestBody ValidatedPayload payload) {
        return "ok";
    }

    @PostMapping("/validated-class-level")
    public String validatedClassLevel(@Validated @RequestBody ClassLevelValidatedPayload payload) {
        return "ok";
    }

    @GetMapping("/type-mismatch")
    public String typeMismatch(@RequestParam Integer value) {
        return "value=" + value;
    }

    public record ValidatedPayload(@NotBlank String name) {}
}
