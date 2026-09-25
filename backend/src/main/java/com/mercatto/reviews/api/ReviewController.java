package com.mercatto.reviews.api;

import com.mercatto.reviews.service.ReviewService;
import com.mercatto.users.service.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Duration;
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

    @PostMapping(value = "/products/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReviewService.ReviewView create(@PathVariable Long productId,
                                            @Valid @RequestBody CreateReviewRequest request,
                                            Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return reviewService.createReview(productId, authenticatedUser.userId(), request.stars(), request.title(),
                request.text());
    }

    /**
     * Same as the JSON endpoint, plus up to 5 photos/videos: part {@code review} carries the
     * {@link CreateReviewRequest} as JSON and zero or more {@code files} parts carry the media.
     */
    @PostMapping(value = "/products/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReviewService.ReviewView createWithMedia(@PathVariable Long productId,
                                                     @Valid @RequestPart("review") CreateReviewRequest request,
                                                     @RequestPart(value = "files", required = false)
                                                     List<MultipartFile> files,
                                                     Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        List<ReviewService.MediaUpload> media = files == null ? List.of() : files.stream()
                .filter(file -> !file.isEmpty())
                .map(file -> new ReviewService.MediaUpload(file, file.getSize()))
                .toList();
        return reviewService.createReview(productId, authenticatedUser.userId(), request.stars(), request.title(),
                request.text(), media);
    }

    /**
     * Public: serves a review photo/video. The content type comes from the magic-byte detection
     * done at upload time (never from the client), {@code nosniff} stops browsers from
     * reinterpreting it, and because the body is a {@link Resource} Spring MVC answers
     * {@code Range} requests with 206 partial content, which video seeking needs.
     */
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Resource> media(@PathVariable Long mediaId) {
        ReviewService.MediaContent content = reviewService.loadMedia(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(content.resource());
    }

    @PostMapping("/{reviewId}/helpful")
    public ReviewService.ReviewView markHelpful(@PathVariable Long reviewId) {
        return reviewService.markHelpful(reviewId);
    }

    public record CreateReviewRequest(@Min(1) @Max(5) int stars,
                                       @NotBlank @Size(max = 255) String title,
                                       @Size(max = 2000) String text) {}
}
