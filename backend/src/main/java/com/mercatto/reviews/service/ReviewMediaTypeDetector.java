package com.mercatto.reviews.service;

import com.mercatto.reviews.domain.ReviewMediaType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies an upload by its leading bytes ("magic numbers"), never by the client-declared
 * Content-Type or the file extension, both of which are trivially spoofed. Only an explicit
 * allowlist is accepted (JPEG, PNG, WebP, MP4, WebM); anything else — SVG, GIF, HEIC, AVIF,
 * QuickTime, PDF, HTML, ... — is rejected.
 */
final class ReviewMediaTypeDetector {

    /** How many leading bytes callers should read before calling {@link #detect(byte[])}. */
    static final int HEADER_SIZE = 64;

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;

    private static final int MIN_HEADER_SIZE = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = ascii("RIFF");
    private static final byte[] WEBP = ascii("WEBP");
    private static final byte[] FTYP = ascii("ftyp");
    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] WEBM_DOCTYPE = ascii("webm");

    // ISO-BMFF major brands that are plain MP4 video. Deliberately excludes HEIC/AVIF image
    // brands (heic, mif1, avif, ...) and QuickTime ("qt  "), which share the ftyp container.
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "mmp4", "dash");

    record DetectedMedia(ReviewMediaType type, String contentType, long maxBytes) {}

    private ReviewMediaTypeDetector() {
    }

    static Optional<DetectedMedia> detect(byte[] header) {
        if (header == null || header.length < MIN_HEADER_SIZE) {
            return Optional.empty();
        }
        if (startsWith(header, 0, JPEG)) {
            return Optional.of(new DetectedMedia(ReviewMediaType.IMAGE, "image/jpeg", MAX_IMAGE_BYTES));
        }
        if (startsWith(header, 0, PNG)) {
            return Optional.of(new DetectedMedia(ReviewMediaType.IMAGE, "image/png", MAX_IMAGE_BYTES));
        }
        if (startsWith(header, 0, RIFF) && startsWith(header, 8, WEBP)) {
            return Optional.of(new DetectedMedia(ReviewMediaType.IMAGE, "image/webp", MAX_IMAGE_BYTES));
        }
        if (startsWith(header, 4, FTYP)
                && MP4_BRANDS.contains(new String(header, 8, 4, StandardCharsets.ISO_8859_1))) {
            return Optional.of(new DetectedMedia(ReviewMediaType.VIDEO, "video/mp4", MAX_VIDEO_BYTES));
        }
        if (startsWith(header, 0, EBML) && contains(header, Math.min(header.length, HEADER_SIZE), WEBM_DOCTYPE)) {
            return Optional.of(new DetectedMedia(ReviewMediaType.VIDEO, "video/webm", MAX_VIDEO_BYTES));
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        return Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static boolean contains(byte[] data, int limit, byte[] needle) {
        for (int i = 0; i + needle.length <= limit; i++) {
            if (startsWith(data, i, needle)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
