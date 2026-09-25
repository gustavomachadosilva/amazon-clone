package com.mercatto.config;

import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@link GlobalExceptionHandler} maps business exceptions raised by controllers to
 * a standardized {@link ApiError} body with the right HTTP status, and that no stack trace ever
 * leaks into the response - including the catch-all 500 fallback.
 *
 * Exercised through {@link ThrowingTestController} (rather than a real module endpoint) so each
 * mapped exception type can be triggered directly and independently of any module's business
 * setup.
 */
@WebMvcTest(controllers = ThrowingTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @Test
    void illegalArgument_mapsTo400WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("bad argument"))
                .andExpect(jsonPath("$.path").value("/test/illegal-argument"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void illegalState_mapsTo409WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("bad state"))
                .andExpect(jsonPath("$.path").value("/test/illegal-state"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void insufficientStock_mapsTo409WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/insufficient-stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("not enough stock"))
                .andExpect(jsonPath("$.path").value("/test/insufficient-stock"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void orderNotFound_mapsTo404WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/order-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found: 1"))
                .andExpect(jsonPath("$.path").value("/test/order-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void orderAccessDenied_mapsTo403WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/order-access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Seller 2 has no items in order 1"))
                .andExpect(jsonPath("$.path").value("/test/order-access-denied"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidFulfillmentTransition_mapsTo409WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/invalid-fulfillment-transition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot advance order 1 from NOT_SHIPPED to DELIVERED"))
                .andExpect(jsonPath("$.path").value("/test/invalid-fulfillment-transition"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void emailAlreadyExists_mapsTo409WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/email-already-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("email already registered"))
                .andExpect(jsonPath("$.path").value("/test/email-already-exists"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void forbiddenRole_mapsTo403WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/forbidden-role"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Apenas vendedores podem criar produtos"))
                .andExpect(jsonPath("$.path").value("/test/forbidden-role"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void dataIntegrityViolation_mapsTo409WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/data-integrity-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("A conflicting record already exists"))
                .andExpect(jsonPath("$.path").value("/test/data-integrity-violation"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void methodArgumentNotValid_mapsTo400WithStandardBody() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/test/validated"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJson_mapsTo400NotInternalServerError() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/validated"));
    }

    @Test
    void typeMismatch_mapsTo400NotInternalServerError() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/type-mismatch"));
    }

    @Test
    void unexpectedException_mapsTo500WithGenericMessageAndNoStackTrace() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/test/unexpected"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("at com.mercatto"))))
                .andExpect(content().string(not(containsString("boom"))));
    }

    @Test
    void userNotFound_mapsTo404WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/user-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Usuário não encontrado: 1"))
                .andExpect(jsonPath("$.path").value("/test/user-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void productNotFound_mapsTo404WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/product-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found: 1"))
                .andExpect(jsonPath("$.path").value("/test/product-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidReviewMedia_mapsTo400WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/invalid-review-media"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG, WebP, MP4 or WebM files are allowed"))
                .andExpect(jsonPath("$.path").value("/test/invalid-review-media"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void reviewMediaNotFound_mapsTo404WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/review-media-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Review media not found: 1"))
                .andExpect(jsonPath("$.path").value("/test/review-media-not-found"));
    }

    @Test
    void maxUploadSizeExceeded_mapsTo413WithStandardBody() throws Exception {
        mockMvc.perform(get("/test/max-upload-size-exceeded"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.message").value("File too large: images up to 5 MB, videos up to 50 MB"))
                .andExpect(jsonPath("$.path").value("/test/max-upload-size-exceeded"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void methodArgumentNotValid_withClassLevelViolation_includesGlobalErrorMessage() throws Exception {
        mockMvc.perform(post("/test/validated-class-level")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"anything\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("class-level constraint violated")))
                .andExpect(jsonPath("$.path").value("/test/validated-class-level"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void illegalArgumentWithoutMessage_mapsTo400WithNullMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-argument-no-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/test/illegal-argument-no-message"))
                .andExpect(content().string(containsString("\"message\":null")));
    }

    @Test
    void illegalStateWithoutMessage_mapsTo409WithNullMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-state-no-message"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value("/test/illegal-state-no-message"))
                .andExpect(content().string(containsString("\"message\":null")));
    }

    @Test
    void pathOf_withNonServletWebRequest_fallsBackToDescriptionWithoutThrowing() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/fallback/path");

        ResponseEntity<Object> response = handler.handleExceptionInternal(
                new RuntimeException("boom"), null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);

        ApiError body = (ApiError) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path()).isEqualTo("uri=/fallback/path");
    }

    @Test
    void handleExceptionInternal_withNonStandardStatusCode_doesNotThrowAndUsesNumericFallback() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpStatusCode nonStandard = HttpStatusCode.valueOf(599);
        WebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/test/non-standard"));

        ResponseEntity<Object> response = handler.handleExceptionInternal(
                new RuntimeException(), null, new HttpHeaders(), nonStandard, request);

        assertThat(response.getStatusCode().value()).isEqualTo(599);
        ApiError body = (ApiError) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("599");
    }
}
