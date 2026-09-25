package com.mercatto.reviews.service;

import com.mercatto.reviews.domain.ReviewMediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewMediaTypeDetectorTest {

    private static byte[] header(int... prefix) {
        byte[] bytes = new byte[64];
        for (int i = 0; i < prefix.length; i++) {
            bytes[i] = (byte) prefix[i];
        }
        return bytes;
    }

    private static byte[] withAscii(byte[] bytes, int offset, String ascii) {
        byte[] chars = ascii.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(chars, 0, bytes, offset, chars.length);
        return bytes;
    }

    private static byte[] ftyp(String brand) {
        return withAscii(withAscii(header(0, 0, 0, 0x20), 4, "ftyp"), 8, brand);
    }

    private static void assertDetected(byte[] bytes, ReviewMediaType type, String contentType) {
        Optional<ReviewMediaTypeDetector.DetectedMedia> detected = ReviewMediaTypeDetector.detect(bytes);
        assertThat(detected).isPresent();
        assertThat(detected.get().type()).isEqualTo(type);
        assertThat(detected.get().contentType()).isEqualTo(contentType);
    }

    @Test
    void detectsJpeg() {
        assertDetected(header(0xFF, 0xD8, 0xFF, 0xE0), ReviewMediaType.IMAGE, "image/jpeg");
        assertThat(ReviewMediaTypeDetector.detect(header(0xFF, 0xD8, 0xFF)).get().maxBytes())
                .isEqualTo(5L * 1024 * 1024);
    }

    @Test
    void detectsPng() {
        assertDetected(header(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), ReviewMediaType.IMAGE, "image/png");
    }

    @Test
    void detectsWebp() {
        assertDetected(withAscii(withAscii(header(), 0, "RIFF"), 8, "WEBP"), ReviewMediaType.IMAGE, "image/webp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "dash"})
    void detectsMp4Brands(String brand) {
        assertDetected(ftyp(brand), ReviewMediaType.VIDEO, "video/mp4");
        assertThat(ReviewMediaTypeDetector.detect(ftyp(brand)).get().maxBytes()).isEqualTo(50L * 1024 * 1024);
    }

    @Test
    void detectsWebm() {
        byte[] bytes = withAscii(header(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81, 0x01), 24, "webm");
        assertDetected(bytes, ReviewMediaType.VIDEO, "video/webm");
    }

    @ParameterizedTest
    @ValueSource(strings = {"heic", "heix", "mif1", "avif", "qt  "})
    void rejectsNonMp4FtypBrands(String brand) {
        assertThat(ReviewMediaTypeDetector.detect(ftyp(brand))).isEmpty();
    }

    @Test
    void rejectsEbmlWithoutWebmDocType() {
        // Matroska (.mkv) shares the EBML header but declares "matroska" as its DocType.
        byte[] bytes = withAscii(header(0x1A, 0x45, 0xDF, 0xA3), 24, "matroska");
        assertThat(ReviewMediaTypeDetector.detect(bytes)).isEmpty();
    }

    @Test
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>"
                .getBytes(StandardCharsets.US_ASCII);
        assertThat(ReviewMediaTypeDetector.detect(svg)).isEmpty();
    }

    @Test
    void rejectsGif() {
        assertThat(ReviewMediaTypeDetector.detect(withAscii(header(), 0, "GIF89a"))).isEmpty();
    }

    @Test
    void rejectsPdf() {
        assertThat(ReviewMediaTypeDetector.detect(withAscii(header(), 0, "%PDF-1.7"))).isEmpty();
    }

    @Test
    void rejectsRiffThatIsNotWebp() {
        assertThat(ReviewMediaTypeDetector.detect(withAscii(withAscii(header(), 0, "RIFF"), 8, "WAVE"))).isEmpty();
    }

    @Test
    void rejectsHeaderShorterThanTwelveBytes() {
        assertThat(ReviewMediaTypeDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEmpty();
        assertThat(ReviewMediaTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(ReviewMediaTypeDetector.detect(null)).isEmpty();
    }
}
