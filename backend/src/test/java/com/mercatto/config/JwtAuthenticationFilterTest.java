package com.mercatto.config;

import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.InvalidTokenException;
import com.mercatto.users.service.TokenService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link JwtAuthenticationFilter} directly with servlet mocks (no Spring context) and a
 * Mockito-mocked {@link TokenService}, mirroring how {@link GlobalExceptionHandlerTest} exercises
 * the exception handler without booting real module beans.
 */
class JwtAuthenticationFilterTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);

    @Test
    void publicPath_withoutHeader_callsChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void protectedPath_withoutHeader_returns401AndDoesNotCallChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"status\":401");
    }

    @Test
    void protectedPath_withMalformedHeader_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "not-a-bearer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_withInvalidToken_returns401() throws ServletException, IOException {
        when(tokenService.validate("bad-token")).thenThrow(new InvalidTokenException("expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("expired");
    }

    @Test
    void protectedPath_withValidToken_callsChainWithAuthenticatedPrincipal() throws ServletException, IOException {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(7L, UserRole.BUYER);
        when(tokenService.validate("good-token")).thenReturn(authenticatedUser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        jakarta.servlet.http.HttpServletRequest forwardedRequest =
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(forwardedRequest).isNotNull();
        Principal principal = forwardedRequest.getUserPrincipal();
        assertThat(principal).isEqualTo(authenticatedUser);
    }
}
