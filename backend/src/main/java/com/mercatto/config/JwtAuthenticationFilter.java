package com.mercatto.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.InvalidTokenException;
import com.mercatto.users.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;

/**
 * Validates the {@code Authorization: Bearer <token>} header on every request except the public
 * endpoints listed below, and — when valid — exposes the resulting {@link AuthenticatedUser} as
 * the request's {@link Principal} so controllers read {@code userId}/{@code role} from the token
 * instead of trusting a value supplied by the client.
 *
 * <p>Deliberately a plain {@link OncePerRequestFilter} rather than Spring Security: the project
 * keeps only {@code spring-security-crypto} (for BCrypt), not the full Security starter.
 */
@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublic(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(request, response, "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = tokenService.validate(token);
        } catch (InvalidTokenException e) {
            writeUnauthorized(request, response, e.getMessage());
            return;
        }

        filterChain.doFilter(new PrincipalRequestWrapper(request, authenticatedUser), response);
    }

    private boolean isPublic(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equals(method) && ("/api/users/register".equals(path) || "/api/users/login".equals(path))) {
            return true;
        }
        return "GET".equals(method) && path.startsWith("/api/catalog/products");
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        ApiError body = new ApiError(Instant.now(), HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), message, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static final class PrincipalRequestWrapper extends HttpServletRequestWrapper {

        private final Principal principal;

        private PrincipalRequestWrapper(HttpServletRequest request, Principal principal) {
            super(request);
            this.principal = principal;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }
    }
}
