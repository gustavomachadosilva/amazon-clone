package com.mercatto.reviews.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskReviewMediaStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskReviewMediaStorage storage() {
        return new LocalDiskReviewMediaStorage(tempDir.resolve("media").toString());
    }

    @Test
    void createsTheMediaDirectory() {
        storage();

        assertThat(tempDir.resolve("media")).isDirectory();
    }

    @Test
    void storeLoadAndDeleteRoundTrip() throws IOException {
        LocalDiskReviewMediaStorage storage = storage();
        byte[] content = {1, 2, 3, 4, 5};

        String key = storage.store(new ByteArrayInputStream(content));

        assertThat(key).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(tempDir.resolve("media").resolve(key)).exists();
        Resource resource = storage.load(key);
        try (InputStream in = resource.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }

        storage.delete(key);

        assertThat(tempDir.resolve("media").resolve(key)).doesNotExist();
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(ReviewMediaNotFoundException.class);
    }

    @Test
    void eachStoreUsesANewKey() throws IOException {
        LocalDiskReviewMediaStorage storage = storage();

        String first = storage.store(new ByteArrayInputStream(new byte[]{1}));
        String second = storage.store(new ByteArrayInputStream(new byte[]{1}));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void storeRemovesPartialFileWhenTheStreamFails() {
        LocalDiskReviewMediaStorage storage = storage();
        InputStream failing = new InputStream() {
            private int reads;

            @Override
            public int read() throws IOException {
                if (reads++ > 10) {
                    throw new IOException("connection reset");
                }
                return 7;
            }
        };

        assertThatThrownBy(() -> storage.store(failing)).isInstanceOf(IOException.class);

        try (var files = Files.list(tempDir.resolve("media"))) {
            assertThat(files).isEmpty();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void pathTraversalKeysAreRejected() throws IOException {
        LocalDiskReviewMediaStorage storage = storage();
        Path outside = Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> storage.load("../secret.txt")).isInstanceOf(ReviewMediaNotFoundException.class);
        assertThatThrownBy(() -> storage.load("../x")).isInstanceOf(ReviewMediaNotFoundException.class);

        storage.delete("../secret.txt");
        assertThat(outside).exists();
    }

    @Test
    void deletingAMissingKeyDoesNotThrow() {
        LocalDiskReviewMediaStorage storage = storage();

        assertThatCode(() -> storage.delete("123e4567-e89b-12d3-a456-426614174000")).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
    }

    @Test
    void loadingAnUnknownKeyThrowsNotFound() {
        LocalDiskReviewMediaStorage storage = storage();

        assertThatThrownBy(() -> storage.load("123e4567-e89b-12d3-a456-426614174000"))
                .isInstanceOf(ReviewMediaNotFoundException.class);
    }
}
