package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Seeds the catalog with sample products fetched from the public DummyJSON API, so the
 * storefront never renders empty against a freshly created database. Only active on the
 * {@code dev} profile — never runs in production.
 */
@Component
@Profile("dev")
public class DummyJsonSeeder {

    private static final Logger log = LoggerFactory.getLogger(DummyJsonSeeder.class);
    private final ProductRepository productRepository;
    private final RestClient restClient;

    public DummyJsonSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
        this.restClient = RestClient.create();
    }

    public void seedProducts(Long sellerId) {
        if (productRepository.count() > 0) {
            log.info("Database already populated. Skipping DummyJSON seed.");
            return;
        }

        log.info("Fetching products from DummyJSON...");
        
        try {
            DummyJsonResponse response = restClient.get()
                    .uri("https://dummyjson.com/products?limit=50")
                    .retrieve()
                    .body(DummyJsonResponse.class);

            if (response != null && response.products() != null) {
                List<Product> productsToSave = response.products().stream().map(dto -> {
                    String imageUrl = null;
                    if (dto.images() != null && !dto.images().isEmpty()) {
                        imageUrl = dto.images().getFirst();
                    }

                    return Product.builder()
                            .name(dto.title())
                            .description(dto.description())
                            .price(dto.price())
                            .stockQuantity(dto.stock())
                            .category(dto.category())
                            .imageUrl(imageUrl)
                            .sellerId(sellerId)
                            .build();
                }).collect(Collectors.toList());

                productRepository.saveAll(productsToSave);
                log.info("DummyJSON seed completed. {} products inserted.", productsToSave.size());
            }
        } catch (Exception e) {
            log.error("Failed to seed database from DummyJSON", e);
        }
    }

    public record DummyJsonResponse(List<DummyJsonProduct> products) {}

    public record DummyJsonProduct(
            String title,
            String description,
            BigDecimal price,
            Integer stock,
            String category,
            List<String> images
    ) {}
}
