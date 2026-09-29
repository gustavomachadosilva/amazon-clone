package com.mercatto.catalog.repository;

import com.mercatto.catalog.domain.Product;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    /**
     * Free-text match over name, brand, category and description (#220): every full-text term
     * must match (stemmed, unaccented, prefix) through {@code catalog.product_fts_matches}, and
     * every literal term ({@code %}/{@code _}) must be a substring of
     * {@code catalog.product_search_text}. The SQL functions live in
     * {@code db/post-ddl/catalog-search.sql}.
     */
    public static Specification<Product> matchesText(ProductTextQuery query) {
        return (root, cq, cb) -> {
            if (query == null) {
                return null;
            }
            List<Predicate> predicates = new ArrayList<>();
            if (query.hasFtsTerms()) {
                predicates.add(cb.isTrue(cb.function("catalog.product_fts_matches", Boolean.class,
                        searchFields(root, cb, cb.literal(query.tsQuery())))));
            }
            if (!query.likePatterns().isEmpty()) {
                Expression<String> searchText = cb.function("catalog.product_search_text", String.class,
                        searchFields(root, cb));
                for (String pattern : query.likePatterns()) {
                    Expression<String> normalizedPattern = cb.lower(
                            cb.function("catalog.immutable_unaccent", String.class, cb.literal(pattern)));
                    predicates.add(cb.like(searchText, normalizedPattern, '\\'));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Orders by full-text rank ({@code ts_rank}, name weighted above brand/category above
     * description), then by id for a stable pagination. Contributes no predicate; a no-op without
     * full-text terms and in the count query. Only meaningful with an unsorted {@code Pageable}:
     * Spring Data replaces this ordering when the page request carries its own {@code Sort}.
     */
    public static Specification<Product> orderByRelevance(ProductTextQuery query) {
        return (root, cq, cb) -> {
            if (query != null && query.hasFtsTerms() && !isCountQuery(cq)) {
                cq.orderBy(
                        cb.desc(cb.function("catalog.product_search_rank", Float.class,
                                searchFields(root, cb, cb.literal(query.tsQuery())))),
                        cb.asc(root.get("id")));
            }
            return null;
        };
    }

    private static boolean isCountQuery(CriteriaQuery<?> cq) {
        return cq != null && (Long.class.equals(cq.getResultType()) || long.class.equals(cq.getResultType()));
    }

    private static Expression<?>[] searchFields(Root<Product> root, CriteriaBuilder cb, Expression<?>... extra) {
        List<Expression<?>> args = new ArrayList<>(List.of(
                root.get("name"), root.get("brand"), root.get("category"), root.get("description")));
        args.addAll(List.of(extra));
        return args.toArray(Expression<?>[]::new);
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
