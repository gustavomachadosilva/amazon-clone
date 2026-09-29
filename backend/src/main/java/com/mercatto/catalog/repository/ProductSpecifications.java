package com.mercatto.catalog.repository;

import com.mercatto.catalog.domain.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Optional search predicates, each one a no-op when its argument is {@code null}. Built as
 * Criteria predicates (instead of one JPQL with {@code :param is null or ...}) so absent filters
 * are simply left out of the SQL, which also sidesteps PostgreSQL failing to infer the type of a
 * null bind parameter.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> nameContains(String query) {
        return (root, cq, cb) -> query == null
                ? null
                : cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase() + "%");
    }

    public static Specification<Product> categoryEquals(String category) {
        return (root, cq, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    public static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return (root, cq, cb) -> minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return (root, cq, cb) -> maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> ratingAtLeast(Double minRating) {
        return (root, cq, cb) -> minRating == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
    }

    /** Name contains any of {@code tokens} (case-insensitive); a no-op when the list is empty. */
    public static Specification<Product> nameContainsAny(List<String> tokens) {
        return (root, cq, cb) -> tokens == null || tokens.isEmpty()
                ? null
                : cb.or(tokens.stream()
                        .map(token -> cb.like(cb.lower(root.get("name")), "%" + token.toLowerCase() + "%"))
                        .toArray(Predicate[]::new));
    }

    public static Specification<Product> categoryNot(String category) {
        return (root, cq, cb) -> category == null ? null : cb.notEqual(root.get("category"), category);
    }

    public static Specification<Product> idNot(Long id) {
        return (root, cq, cb) -> id == null ? null : cb.notEqual(root.get("id"), id);
    }

    public static Specification<Product> inStock() {
        return (root, cq, cb) -> cb.greaterThan(root.get("stockQuantity"), 0);
    }
}
