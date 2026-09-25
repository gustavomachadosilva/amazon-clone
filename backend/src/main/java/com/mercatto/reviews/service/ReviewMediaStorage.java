package com.mercatto.reviews.service;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Port for where review photo/video bytes live (Contrato de Modularidade regra 4). The module
 * owns this interface; {@link LocalDiskReviewMediaStorage} is the initial implementation and an
 * object store (S3, etc.) can replace it without touching {@link ReviewServiceImpl}.
 *
 * <p>Keys are opaque, server-generated identifiers — never a client-supplied filename.
 */
public interface ReviewMediaStorage {

    /**
     * Persists the stream's bytes and returns the key under which they can be loaded.
     */
    String store(InputStream content) throws IOException;

    /**
     * Returns the stored bytes, or throws {@link ReviewMediaNotFoundException} if the key is unknown.
     */
    Resource load(String key);

    /**
     * Removes the stored bytes. Idempotent and best-effort: never throws, so it is safe to call
     * from cleanup paths (rollback, failed uploads).
     */
    void delete(String key);
}
