package com.mercatto.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-side reader of the dev seed catalog ({@code seed/amazon-products-sample.csv}), used by the
 * search evaluation (#219) to check that every product/category referenced by the fixtures really
 * exists. Deliberately independent of {@code AmazonProductSeeder} (catalog's internals): tests
 * must not reach into another module's implementation, and a copy of the tiny RFC 4180 parser is
 * cheaper than widening that class's visibility.
 */
final class SeedCatalogCsv {

    static final String PATH = "seed/amazon-products-sample.csv";

    record Row(String name, String price, int stockQuantity, String category, String imageUrl) {}

    private SeedCatalogCsv() {}

    static List<Row> read() {
        InputStream in = SeedCatalogCsv.class.getClassLoader().getResourceAsStream(PATH);
        if (in == null) {
            throw new IllegalStateException("Seed catalog not found on the classpath: " + PATH);
        }
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine(); // header: name,price,stock_quantity,category,image_url
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseLine(line);
                if (fields.size() != 5) {
                    throw new IllegalStateException("Expected 5 fields but got " + fields.size() + ": " + line);
                }
                rows.add(new Row(fields.get(0), fields.get(1), Integer.parseInt(fields.get(2)), fields.get(3),
                        fields.get(4)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    /** Double-quoted fields with {@code ""} escapes; the sample has no embedded newlines. */
    static List<String> parseLine(String line) {
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
        return fields;
    }
}
