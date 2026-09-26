package com.mercatto.catalog.api;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductSearchCriteria;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.ProductSort;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;

@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;

    // page/size are bound explicitly instead of through a Pageable argument: Spring's Pageable
    // resolver would also read ?sort= and turn e.g. sort=price_asc into an ORDER BY on a
    // non-existent "price_asc" property (a 500). Sorting is ProductSort's job here.
    @GetMapping
    public Page<ProductService.ProductView> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                query, category, minPrice, maxPrice, minRating, ProductSort.fromParam(sort));
        return productService.searchWithRating(criteria, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductService.ProductView> getById(@PathVariable Long id) {
        return productService.findByIdWithRating(id)
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
                .brand(request.brand())
                .warrantyMonths(request.warrantyMonths())
                .modelNumber(request.modelNumber())
                .listPrice(request.listPrice())
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
            @Size(max = 1000) String imageUrl,
            @Size(max = 255) String brand,
            @PositiveOrZero Integer warrantyMonths,
            @Size(max = 255) String modelNumber,
            @Positive BigDecimal listPrice) {}

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateProductRequest request,
                                           Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        Optional<ProductService.ProductSummary> existing = productService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(existing.get().sellerId());

        Product changes = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .category(request.category())
                .imageUrl(request.imageUrl())
                .brand(request.brand())
                .warrantyMonths(request.warrantyMonths())
                .modelNumber(request.modelNumber())
                .listPrice(request.listPrice())
                .build();
        return ResponseEntity.ok(productService.update(id, changes));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        Optional<ProductService.ProductSummary> existing = productService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(existing.get().sellerId());

        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            @NotNull @Positive BigDecimal price,
            @NotNull @PositiveOrZero Integer stockQuantity,
            @NotBlank @Size(max = 255) String category,
            @Size(max = 1000) String imageUrl,
            @Size(max = 255) String brand,
            @PositiveOrZero Integer warrantyMonths,
            @Size(max = 255) String modelNumber,
            @Positive BigDecimal listPrice) {}
}
