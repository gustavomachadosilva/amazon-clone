package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.catalog.repository.ProductTextQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.mercatto.catalog.repository.ProductSpecifications.categoryEquals;
import static com.mercatto.catalog.repository.ProductSpecifications.categoryNot;
import static com.mercatto.catalog.repository.ProductSpecifications.idNot;
import static com.mercatto.catalog.repository.ProductSpecifications.inStock;
import static com.mercatto.catalog.repository.ProductSpecifications.matchesText;
import static com.mercatto.catalog.repository.ProductSpecifications.nameContainsAny;
import static com.mercatto.catalog.repository.ProductSpecifications.orderByRelevance;
import static com.mercatto.catalog.repository.ProductSpecifications.priceAtLeast;
import static com.mercatto.catalog.repository.ProductSpecifications.priceAtMost;
import static com.mercatto.catalog.repository.ProductSpecifications.ratingAtLeast;

/**
 * Product ratings are read from the product's own denormalized columns (kept in sync with the
 * Reviews module by {@link ReviewRatingSyncListener} and {@link ProductRatingBackfill}), not
 * resolved from Reviews at read time, so search can filter and sort by rating in SQL.
 */
@Service
@RequiredArgsConstructor
class ProductServiceImpl implements ProductService {

    // Sensible default so seller-created products without an explicit warranty behave the same
    // as seeded ones (see AmazonProductSeeder), rather than silently defaulting to "no warranty".
    private static final int DEFAULT_WARRANTY_MONTHS = 12;

    // Related products from other categories only count when they share the brand or the name:
    // a cross-category item that's merely cheaper or better rated isn't related to anything.
    private static final Set<RelatedReason> CROSS_CATEGORY_REASONS =
            EnumSet.of(RelatedReason.SAME_BRAND, RelatedReason.SIMILAR_NAME);
    private static final int FALLBACK_NAME_TOKENS = 3;
    private static final int FALLBACK_POOL_SIZE = 50;

    private final ProductRepository productRepository;
    private final RelatedProductScorer relatedProductScorer = new RelatedProductScorer();

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
    public Page<ProductView> searchWithRating(ProductSearchCriteria criteria, int page, int size) {
        ProductTextQuery text = ProductTextQuery.parse(criteria.query());
        Page<Product> result = search(criteria, text, page, size);
        // Typo tolerance (#220): only when nothing matched at all, and at most one retry.
        if (result.getTotalElements() == 0 && text != null && text.hasFtsTerms()) {
            ProductTextQuery corrected = correctTypos(text);
            if (corrected != null) {
                result = search(criteria, corrected, page, size);
            }
        }
        return result.map(this::toView);
    }

    private Page<Product> search(ProductSearchCriteria criteria, ProductTextQuery text, int page, int size) {
        Specification<Product> spec = Specification.allOf(
                matchesText(text),
                categoryEquals(criteria.category()),
                priceAtLeast(criteria.minPrice()),
                priceAtMost(criteria.maxPrice()),
                ratingAtLeast(criteria.minRating()));
        // RELEVANCE with free text orders by the full-text rank, set by the specification itself;
        // an unsorted page request keeps Spring Data from replacing that ORDER BY.
        if (criteria.sort() == ProductSort.RELEVANCE && text != null && text.hasFtsTerms()) {
            return productRepository.findAll(spec.and(orderByRelevance(text)), PageRequest.of(page, size));
        }
        return productRepository.findAll(spec, PageRequest.of(page, size, criteria.sort().toSort()));
    }

    /**
     * Replaces each correctable term that isn't a catalog word with the closest one; {@code null}
     * when no term changed (so the caller doesn't repeat the same empty search).
     */
    private ProductTextQuery correctTypos(ProductTextQuery text) {
        Map<String, String> replacements = new HashMap<>();
        for (String term : text.correctableTerms()) {
            productRepository.findClosestIndexedWord(term, ProductTextQuery.maxEdits(term))
                    .filter(word -> !word.equals(term))
                    .ifPresent(word -> replacements.put(term, word));
        }
        return replacements.isEmpty() ? null : text.withReplacedTerms(replacements);
    }

    @Override
    public Optional<ProductView> findByIdWithRating(Long id) {
        return productRepository.findById(id).map(this::toView);
    }

    @Override
    public List<ProductView> findViewsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(ids).stream().map(this::toView).toList();
    }

    @Override
    public List<ProductView> findTopRatedInStock(String category, int limit) {
        Specification<Product> spec = Specification.allOf(categoryEquals(category), inStock());
        return productRepository.findAll(spec, PageRequest.of(0, limit, ProductSort.RATING.toSort()))
                .map(this::toView)
                .getContent();
    }

    @Override
    public List<ProductView> findTopRatedInStockPerCategory(int perCategory, int limit) {
        List<Long> ids = productRepository.findTopRatedInStockIdsPerCategory(perCategory, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        // findAllById doesn't keep the order of the ids; restore the query's ranking.
        Map<Long, Product> byId = new HashMap<>();
        productRepository.findAllById(ids).forEach(product -> byId.put(product.getId(), product));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelatedProduct> findRelated(Long productId, int limit) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        ProductView current = toView(product);
        Double categoryTopRating = productRepository.findTopAverageRatingInCategory(product.getCategory());

        List<Product> pool = productRepository.findTop200ByCategoryAndIdNotAndStockQuantityGreaterThan(
                product.getCategory(), productId, 0);
        List<RelatedProductScorer.Scored> scored = new ArrayList<>(pool.stream()
                .map(candidate -> relatedProductScorer.score(current, toView(candidate), categoryTopRating))
                .toList());

        if (pool.size() < limit) {
            crossCategoryCandidates(product).stream()
                    .map(candidate -> relatedProductScorer.score(current, toView(candidate), categoryTopRating))
                    .filter(candidate -> candidate.hasAnyReason(CROSS_CATEGORY_REASONS))
                    .forEach(scored::add);
        }

        return scored.stream()
                .sorted(RelatedProductScorer.ORDER)
                .limit(limit)
                .map(RelatedProductScorer.Scored::toRelatedProduct)
                .toList();
    }

    // In-stock products outside the current category sharing its brand or one of its most
    // specific name words, deduplicated by id. The scorer then decides whether they really match.
    private List<Product> crossCategoryCandidates(Product product) {
        Map<Long, Product> candidates = new LinkedHashMap<>();
        if (RelatedProductScorer.isRealBrand(product.getBrand())) {
            productRepository.findTop50ByBrandIgnoreCaseAndCategoryNotAndStockQuantityGreaterThan(
                            product.getBrand().trim(), product.getCategory(), 0)
                    .forEach(candidate -> candidates.putIfAbsent(candidate.getId(), candidate));
        }
        List<String> tokens = RelatedProductScorer.longestNameTokens(product.getName(), FALLBACK_NAME_TOKENS);
        if (!tokens.isEmpty()) {
            Specification<Product> spec = Specification.allOf(
                    nameContainsAny(tokens),
                    categoryNot(product.getCategory()),
                    inStock(),
                    idNot(product.getId()));
            productRepository.findAll(spec, PageRequest.of(0, FALLBACK_POOL_SIZE))
                    .forEach(candidate -> candidates.putIfAbsent(candidate.getId(), candidate));
        }
        candidates.remove(product.getId());
        return List.copyOf(candidates.values());
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

    private ProductView toView(Product product) {
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
                product.getAverageRating() != null ? product.getAverageRating() : 0.0,
                product.getReviewCount() != null ? product.getReviewCount() : 0L);
    }
}
