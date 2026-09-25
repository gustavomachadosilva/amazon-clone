package com.mercatto.reviews.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.reviews.service.InvalidReviewMediaException;
import com.mercatto.reviews.service.ReviewMediaNotFoundException;
import com.mercatto.reviews.service.ReviewNotFoundException;
import com.mercatto.reviews.service.ReviewService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

    private static final AuthenticatedUser AUTHOR = new AuthenticatedUser(10L, UserRole.BUYER);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private TokenService tokenService;

    private static ReviewService.ReviewView view() {
        return new ReviewService.ReviewView(1L, 5L, 10L, "Ana", 4, "Great", "Loved it", 0,
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(new ReviewService.MediaView(7L, "IMAGE", "/api/reviews/media/7")));
    }

    private static ReviewController.CreateReviewRequest validRequest() {
        return new ReviewController.CreateReviewRequest(4, "Great", "Loved it");
    }

    @Test
    void listByProduct_returns200() throws Exception {
        when(reviewService.listByProduct(5L)).thenReturn(List.of(view()));

        mockMvc.perform(get("/api/reviews/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorName").value("Ana"));

        verify(reviewService).listByProduct(5L);
    }

    @Test
    void createWithValidRequest_returns200AndCallsServiceWithAuthorIdFromPrincipal() throws Exception {
        when(reviewService.createReview(5L, 10L, 4, "Great", "Loved it")).thenReturn(view());

        mockMvc.perform(post("/api/reviews/products/5")
                        .contentType("application/json")
                        .principal(AUTHOR)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Ana"));

        verify(reviewService).createReview(5L, 10L, 4, "Great", "Loved it");
    }

    static Stream<ReviewController.CreateReviewRequest> invalidRequests() {
        return Stream.of(
                // stars below 1
                new ReviewController.CreateReviewRequest(0, "Great", "Loved it"),
                // stars above 5
                new ReviewController.CreateReviewRequest(6, "Great", "Loved it"),
                // title blank
                new ReviewController.CreateReviewRequest(4, "", "Loved it")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void createWithInvalidRequest_returns400(ReviewController.CreateReviewRequest request) throws Exception {
        mockMvc.perform(post("/api/reviews/products/5")
                        .contentType("application/json")
                        .principal(AUTHOR)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void markHelpfulWithKnownId_returns200() throws Exception {
        when(reviewService.markHelpful(1L)).thenReturn(view());

        mockMvc.perform(post("/api/reviews/1/helpful").principal(AUTHOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(reviewService).markHelpful(eq(1L));
    }

    @Test
    void markHelpfulWithUnknownId_returns404() throws Exception {
        when(reviewService.markHelpful(99L)).thenThrow(new ReviewNotFoundException("Review not found: 99"));

        mockMvc.perform(post("/api/reviews/99/helpful").principal(AUTHOR))
                .andExpect(status().isNotFound());
    }

    // --- multipart create with media ---------------------------------------------------------

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10,
            'J', 'F', 'I', 'F', 0, 1};

    private MockMultipartFile reviewPart(ReviewController.CreateReviewRequest request) throws Exception {
        return new MockMultipartFile("review", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createMultipartWithFile_returns200WithMediaAndPassesUploadsToService() throws Exception {
        when(reviewService.createReview(eq(5L), eq(10L), eq(4), eq("Great"), eq("Loved it"), anyList()))
                .thenReturn(view());
        MockMultipartFile file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", JPEG_BYTES);

        mockMvc.perform(multipart("/api/reviews/products/5")
                        .file(reviewPart(validRequest()))
                        .file(file)
                        .principal(AUTHOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].url").value("/api/reviews/media/7"))
                .andExpect(jsonPath("$.media[0].type").value("IMAGE"));

        ArgumentCaptor<List<ReviewService.MediaUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewService).createReview(eq(5L), eq(10L), eq(4), eq("Great"), eq("Loved it"), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).sizeBytes()).isEqualTo(JPEG_BYTES.length);
        try (InputStream in = captor.getValue().get(0).content().getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(JPEG_BYTES);
        }
    }

    @Test
    void createMultipartWithoutFiles_passesEmptyMediaList() throws Exception {
        when(reviewService.createReview(5L, 10L, 4, "Great", "Loved it", List.of())).thenReturn(view());

        mockMvc.perform(multipart("/api/reviews/products/5")
                        .file(reviewPart(validRequest()))
                        .principal(AUTHOR))
                .andExpect(status().isOk());

        verify(reviewService).createReview(5L, 10L, 4, "Great", "Loved it", List.of());
    }

    @Test
    void createMultipartWithInvalidReviewPart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/reviews/products/5")
                        .file(reviewPart(new ReviewController.CreateReviewRequest(0, "", "x")))
                        .principal(AUTHOR))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void createMultipartWhenServiceRejectsMedia_returns400WithMessage() throws Exception {
        when(reviewService.createReview(eq(5L), eq(10L), eq(4), eq("Great"), eq("Loved it"), anyList()))
                .thenThrow(new InvalidReviewMediaException("Only JPEG, PNG, WebP, MP4 or WebM files are allowed"));
        MockMultipartFile file = new MockMultipartFile("files", "photo.jpg", "image/jpeg",
                "<svg></svg>".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(multipart("/api/reviews/products/5")
                        .file(reviewPart(validRequest()))
                        .file(file)
                        .principal(AUTHOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG, WebP, MP4 or WebM files are allowed"));
    }

    // --- serving media -----------------------------------------------------------------------

    private static final byte[] VIDEO_BYTES = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.US_ASCII);

    @Test
    void getMedia_returns200WithSafeHeaders() throws Exception {
        when(reviewService.loadMedia(7L))
                .thenReturn(new ReviewService.MediaContent(new ByteArrayResource(VIDEO_BYTES), "video/mp4"));

        mockMvc.perform(get("/api/reviews/media/7"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(content().bytes(VIDEO_BYTES));
    }

    @Test
    void getMediaWithRange_returns206PartialContent() throws Exception {
        when(reviewService.loadMedia(7L))
                .thenReturn(new ReviewService.MediaContent(new ByteArrayResource(VIDEO_BYTES), "video/mp4"));

        mockMvc.perform(get("/api/reviews/media/7").header(HttpHeaders.RANGE, "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-9/" + VIDEO_BYTES.length))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(content().bytes("0123456789".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void getUnknownMedia_returns404() throws Exception {
        when(reviewService.loadMedia(99L)).thenThrow(new ReviewMediaNotFoundException("Review media not found: 99"));

        mockMvc.perform(get("/api/reviews/media/99"))
                .andExpect(status().isNotFound());
    }
}
