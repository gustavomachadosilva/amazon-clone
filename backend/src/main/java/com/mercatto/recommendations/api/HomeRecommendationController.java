package com.mercatto.recommendations.api;

import com.mercatto.recommendations.service.HomeRecommendationService;
import com.mercatto.users.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * The Home's product shelf (Card #225). Optionally authenticated (see
 * {@code JwtAuthenticationFilter}): anonymous visitors get the "Top rated" layer, a signed-in user
 * a personalized one when their history allows it. A bad token is still a 401, never a silent
 * downgrade to anonymous.
 */
@RestController
@RequiredArgsConstructor
public class HomeRecommendationController {

    static final int MAX_LIMIT = 24;

    private final HomeRecommendationService homeRecommendationService;

    @GetMapping("/api/recommendations/home")
    public HomeRecommendationService.HomeRecommendations home(@RequestParam(defaultValue = "12") int limit,
                                                              Principal principal) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        Long userId = principal instanceof AuthenticatedUser user ? user.userId() : null;
        return homeRecommendationService.forHome(userId, limit);
    }
}
