package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.reviews.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalog calls Reviews synchronously through {@link ReviewService} (its public API) to resolve
 * each product's aggregate rating at read time, mirroring how {@code CartServiceImpl} resolves
 * product name/price through Catalog's own public API. This is a read-only, never-mutating call,
 * so it is safe inside this module's own transactions.
 */
@Service
@RequiredArgsConstructor
class ProductServiceImpl implements ProductService {

    // Sensible default so seller-created products without an explicit warranty behave the same
    // as seeded ones (see AmazonProductSeeder), rather than silently defaulting to "no warranty".
    private static final int DEFAULT_WARRANTY_MONTHS = 12;

    private final ProductRepository productRepository;
    private final ReviewService reviewService;

    @Override
    public Page<Product> search(String query, String category, Pageable pageable) {
        return productRepository.search(query, category, pageable);
    }

    @Override
    public Optional<ProductSummary> findById(Long id) {
        return productRepository.findById(id).map(this::toSummary);
    }

    @Override
    public Product create(Product product) {
        validateListPrice(product.getListPrice(), product.getPrice());
        if (product.getWarrantyMonths() == null) {
            product.setWarrantyMonths(DEFAULT_WARRANTY_MONTHS);
        }
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product update(Long id, Product changes) {
        validateListPrice(changes.getListPrice(), changes.getPrice());
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setPrice(changes.getPrice());
        existing.setStockQuantity(changes.getStockQuantity());
        existing.setCategory(changes.getCategory());
        existing.setImageUrl(changes.getImageUrl());
        existing.setBrand(changes.getBrand());
        existing.setWarrantyMonths(changes.getWarrantyMonths() != null ? changes.getWarrantyMonths() : DEFAULT_WARRANTY_MONTHS);
        existing.setModelNumber(changes.getModelNumber());
        existing.setListPrice(changes.getListPrice());
        return productRepository.save(existing);
    }

    // listPrice is a "was" price used to render a strikethrough discount; one that isn't
    // actually higher than the selling price would be a misleading discount, so it's rejected
    // outright rather than silently ignored.
    private void validateListPrice(BigDecimal listPrice, BigDecimal price) {
        if (listPrice != null && price != null && listPrice.compareTo(price) <= 0) {
            throw new IllegalArgumentException("listPrice must be greater than price");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException("Product not found: " + id);
        }
        productRepository.deleteById(id);
    }

    @Override
    public Page<ProductSummary> findBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable).map(this::toSummary);
    }

    // REQUIRES_NEW: this is invoked from an AFTER_COMMIT event listener, where the
    // triggering transaction's resources are still bound to the thread until the
    // listener returns — a default-propagation @Transactional here would silently
    // join that (already-physically-committed) transaction instead of opening a
    // fresh one, so retries would keep reusing the same stale persistence context.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        int updated = product.getStockQuantity() - quantity;
        if (updated < 0) {
            throw new IllegalStateException("Insufficient stock for product " + productId);
        }
        product.setStockQuantity(updated);
        productRepository.save(product);
    }

    @Override
    public List<String> listCategories() {
        return productRepository.findDistinctCategories();
    }

    @Override
    public Page<ProductView> searchWithRating(String query, String category, Pageable pageable) {
        Page<Product> page = productRepository.search(query, category, pageable);
        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();
        Map<Long, ReviewService.RatingAggregate> aggregates = reviewService.getAggregates(productIds);
        return page.map(product -> toView(product,
                aggregates.getOrDefault(product.getId(), ReviewService.RatingAggregate.empty(product.getId()))));
    }

    @Override
    public Optional<ProductView> findByIdWithRating(Long id) {
        return productRepository.findById(id)
                .map(product -> toView(product, reviewService.getAggregate(id)));
    }

    private ProductSummary toSummary(Product product) {
        return new ProductSummary(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCategory(),
                product.getImageUrl(),
                product.getBrand(),
                product.getWarrantyMonths(),
                product.getModelNumber(),
                product.getListPrice(),
                product.getSellerId(),
                product.getCreatedAt());
    }

    private ProductView toView(Product product, ReviewService.RatingAggregate aggregate) {
        return new ProductView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCategory(),
                product.getImageUrl(),
                product.getBrand(),
                product.getWarrantyMonths(),
                product.getModelNumber(),
                product.getListPrice(),
                product.getSellerId(),
                product.getCreatedAt(),
                aggregate.averageRating(),
                aggregate.reviewCount());
    }
}
