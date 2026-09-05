package com.mercatto.config;

import com.mercatto.orders.service.InsufficientStockException;
import com.mercatto.users.service.EmailAlreadyExistsException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/email-already-exists")
    public String emailAlreadyExists() {
        throw new EmailAlreadyExistsException("email already registered");
    }

    @GetMapping("/data-integrity-violation")
    public String dataIntegrityViolation() {
        throw new DataIntegrityViolationException("unique constraint violated");
    }

    @GetMapping("/unexpected")
    public String unexpected() {
        throw new RuntimeException("boom - sensitive internal detail");
    }

    @PostMapping("/validated")
    public String validated(@Validated @RequestBody ValidatedPayload payload) {
        return "ok";
    }

    @GetMapping("/type-mismatch")
    public String typeMismatch(@RequestParam Integer value) {
        return "value=" + value;
    }

    public record ValidatedPayload(@NotBlank String name) {}
}
