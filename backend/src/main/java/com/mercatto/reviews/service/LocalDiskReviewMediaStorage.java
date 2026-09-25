package com.mercatto.reviews.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores review media as flat files under {@code mercatto.media.dir} (env {@code MEDIA_DIR}),
 * one file per upload named by a random UUID. Keys are checked against the UUID format before
 * every filesystem access, so a crafted key (e.g. {@code ../../etc/passwd}) can never escape
 * the media directory.
 */
@Slf4j
@Component
class LocalDiskReviewMediaStorage implements ReviewMediaStorage {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path baseDir;

    LocalDiskReviewMediaStorage(@Value("${mercatto.media.dir}") String mediaDir) {
        this.baseDir = Path.of(mediaDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create review media directory " + baseDir, e);
        }
    }

    @Override
    public String store(InputStream content) throws IOException {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        try {
            Files.copy(content, target);
        } catch (IOException | RuntimeException e) {
            // Never leave a half-written file behind.
            Files.deleteIfExists(target);
            throw e;
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        if (!isValidKey(key)) {
            throw new ReviewMediaNotFoundException("Review media not found");
        }
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new ReviewMediaNotFoundException("Review media not found");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        if (!isValidKey(key)) {
            log.warn("Refusing to delete review media with invalid key");
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete review media {}", key, e);
        }
    }

    private Path resolve(String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Invalid review media key");
        }
        return baseDir.resolve(key);
    }

    private static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }
}
