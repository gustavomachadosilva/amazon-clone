package com.mercatto.reviews.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.reviews.service.ReviewNotFoundException;
import com.mercatto.reviews.service.ReviewService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                Instant.parse("2026-01-01T00:00:00Z"));
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
}
