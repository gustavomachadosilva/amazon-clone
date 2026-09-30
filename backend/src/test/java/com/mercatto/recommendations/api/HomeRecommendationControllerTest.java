package com.mercatto.recommendations.api;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.recommendations.service.HomeRecommendationService;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeLayer;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeRecommendations;
import com.mercatto.recommendations.service.HomeRecommendationService.RecommendationReason;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeRecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomeRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeRecommendationService homeRecommendationService;

    @MockBean
    private TokenService tokenService;

    private static ProductService.ProductView productView(long id) {
        return new ProductService.ProductView(id, "Widget", "A useful widget", BigDecimal.TEN, 10, "tools",
                "http://example.com/img.png", "Acme", 12, "MDL-1", BigDecimal.valueOf(15),
                1L, Instant.parse("2026-01-01T00:00:00Z"), 4.5, 3L);
    }

    @Test
    void anonymousRequestAsksForTheTopRatedShelfWithADefaultLimitOfTwelve() throws Exception {
        when(homeRecommendationService.forHome(null, 12)).thenReturn(new HomeRecommendations(HomeLayer.TOP_RATED,
                List.of(new HomeRecommendationService.Item(productView(2L), RecommendationReason.TOP_RATED))));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layer").value("TOP_RATED"))
                .andExpect(jsonPath("$.items[0].product.id").value(2))
                .andExpect(jsonPath("$.items[0].product.averageRating").value(4.5))
                .andExpect(jsonPath("$.items[0].reason").value("TOP_RATED"))
                .andExpect(jsonPath("$.title").doesNotExist());

        verify(homeRecommendationService).forHome(isNull(), anyInt());
    }

    @Test
    void signedInRequestUsesTheUserIdFromTheToken() throws Exception {
        when(homeRecommendationService.forHome(7L, 6)).thenReturn(new HomeRecommendations(HomeLayer.PERSONALIZED,
                List.of(new HomeRecommendationService.Item(productView(3L), RecommendationReason.BOUGHT_TOGETHER))));

        mockMvc.perform(get("/api/recommendations/home").param("limit", "6")
                        .principal(new AuthenticatedUser(7L, UserRole.BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layer").value("PERSONALIZED"))
                .andExpect(jsonPath("$.items[0].product.id").value(3))
                .andExpect(jsonPath("$.items[0].reason").value("BOUGHT_TOGETHER"));

        verify(homeRecommendationService).forHome(7L, 6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "25", "abc"})
    void limitOutOfRange_returns400(String limit) throws Exception {
        mockMvc.perform(get("/api/recommendations/home").param("limit", limit))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(homeRecommendationService);
    }

    @Test
    void theLargestLimitIsAccepted() throws Exception {
        when(homeRecommendationService.forHome(null, 24))
                .thenReturn(new HomeRecommendations(HomeLayer.TOP_RATED, List.of()));

        mockMvc.perform(get("/api/recommendations/home").param("limit", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
