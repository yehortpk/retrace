package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticates a request carrying an {@code X-API-Key} header — the agent's path into the same
 * endpoints the UI uses.
 *
 * <p>Two properties are worth stating because they are choices rather than defaults:
 *
 * <ul>
 *   <li><b>The context is never persisted to the session.</b> A key request leaves no session behind,
 *       so the agent's {@code curl} stays stateless however many times it runs.
 *   <li><b>A bad key is an error, not a fallback.</b> If the header is present and wrong the chain
 *       stops with a 401, <em>even when a valid session cookie accompanies it</em>. A request that
 *       presents a credential and gets it wrong is a mistake its caller needs to see, not an
 *       invitation to act as whoever the cookie belongs to.
 * </ul>
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(API_KEY_HEADER);
        if (presented == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AppUser> owner = apiKeyService.authenticate(presented);
        if (owner.isEmpty()) {
            // The key itself is never logged, here or anywhere: the path and the outcome are the
            // whole of what a reader of the log needs.
            log.info("Rejected a request to {} carrying an invalid API key", request.getRequestURI());
            writeUnauthorized(request, response);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ApiKeyAuthenticationToken(owner.get()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "The API key presented in the %s header is not valid".formatted(API_KEY_HEADER));
        problem.setTitle("Unauthorized");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
