package com.mercatto.catalog.api;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;

@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<Product> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        return productService.search(query, category, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .category(request.category())
                .imageUrl(request.imageUrl())
                .sellerId(authenticatedUser.userId())
                .build();
        return ResponseEntity.ok(productService.create(product));
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            @NotNull @Positive BigDecimal price,
            @NotNull @PositiveOrZero Integer stockQuantity,
            @NotBlank @Size(max = 255) String category,
            @Size(max = 1000) String imageUrl) {}
}
