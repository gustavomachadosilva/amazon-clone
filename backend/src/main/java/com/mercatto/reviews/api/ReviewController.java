package com.mercatto.reviews.api;

import com.mercatto.reviews.service.ReviewService;
import com.mercatto.users.service.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/products/{productId}")
    public List<ReviewService.ReviewView> listByProduct(@PathVariable Long productId) {
        return reviewService.listByProduct(productId);
    }

    @PostMapping("/products/{productId}")
    public ReviewService.ReviewView create(@PathVariable Long productId,
                                            @Valid @RequestBody CreateReviewRequest request,
                                            Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return reviewService.createReview(productId, authenticatedUser.userId(), request.stars(), request.title(),
                request.text());
    }

    @PostMapping("/{reviewId}/helpful")
    public ReviewService.ReviewView markHelpful(@PathVariable Long reviewId) {
        return reviewService.markHelpful(reviewId);
    }

    public record CreateReviewRequest(@Min(1) @Max(5) int stars,
                                       @NotBlank @Size(max = 255) String title,
                                       @Size(max = 2000) String text) {}
}
