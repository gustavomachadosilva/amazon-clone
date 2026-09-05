package com.mercatto.config;

import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
}
