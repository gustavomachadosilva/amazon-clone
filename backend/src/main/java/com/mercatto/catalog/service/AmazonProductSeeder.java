package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the catalog from a curated sample of the Kaggle "Amazon Products 2023" dataset
 * (bundled at {@code seed/amazon-products-sample.csv}, 500 products across 25 categories, cut
 * down from the original ~1.4M-row dataset), so the storefront never renders empty against a
 * freshly created database. Products are distributed round-robin across the given sellers.
 * Only active on the {@code dev} profile — never runs in production.
 */
@Component
@Profile("dev")
public class AmazonProductSeeder {

    private static final Logger log = LoggerFactory.getLogger(AmazonProductSeeder.class);
    private static final String SAMPLE_CSV_PATH = "seed/amazon-products-sample.csv";

    // Default warranty term applied to every seeded product, matching the default used by
    // ProductServiceImpl#create when a seller doesn't specify one. Editable per-product
    // afterwards — this is only the seed-time starting point, not a global constant.
    private static final int DEFAULT_WARRANTY_MONTHS = 12;

    private final ProductRepository productRepository;

    public AmazonProductSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void seedProducts(List<Long> sellerIds) {
        if (productRepository.count() > 0) {
            log.info("Database already populated. Skipping Amazon dataset seed.");
            return;
        }
        if (sellerIds.isEmpty()) {
            throw new IllegalArgumentException("Cannot seed products without at least one seller");
        }

        log.info("Loading Amazon dataset sample from {}...", SAMPLE_CSV_PATH);
        List<Product> products = readSampleCsv(sellerIds);
        productRepository.saveAll(products);
        log.info("Amazon dataset seed completed. {} products inserted.", products.size());
    }

    private List<Product> readSampleCsv(List<Long> sellerIds) {
        List<Product> products = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(SAMPLE_CSV_PATH);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = parseCsvLine(line);
                Long sellerId = sellerIds.get(index % sellerIds.size());
                products.add(Product.builder()
                        .name(fields[0])
                        .price(new BigDecimal(fields[1]))
                        .stockQuantity(Integer.valueOf(fields[2]))
                        .category(fields[3])
                        .imageUrl(fields[4])
                        .brand(guessBrand(fields[0]))
                        .warrantyMonths(DEFAULT_WARRANTY_MONTHS)
                        // modelNumber/listPrice stay null: the CSV sample has no SKU/model
                        // number or "was" price column, so there's no real data to seed them
                        // from. They're left for a seller to fill in via edit.
                        .sellerId(sellerId)
                        .build());
                index++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Amazon dataset sample from " + SAMPLE_CSV_PATH, e);
        }

        return products;
    }

    /** Minimal RFC 4180 parser: only handles double-quoted fields with escaped {@code ""}, which
     * is all the bundled sample ever produces — no embedded newlines to worry about. */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Best-effort brand heuristic: the CSV sample has no dedicated brand column, so this takes
     * the first word of the product name as a stand-in — e.g. "Samsung 65-Inch TV" -> "Samsung".
     * This is deliberately naive and will misfire on generic/descriptive names that don't start
     * with a brand (e.g. "Wireless Mouse" -> "Wireless", "3-Pack Cotton Socks" -> "3-Pack"). It's
     * an acceptable seed-time approximation, not a claim of accuracy; sellers can correct it per
     * product via edit.
     */
    private String guessBrand(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        String firstWord = productName.trim().split("\\s+", 2)[0];
        return firstWord.isBlank() ? null : firstWord;
    }
}
