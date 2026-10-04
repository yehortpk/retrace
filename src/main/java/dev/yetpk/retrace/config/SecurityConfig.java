package dev.yetpk.retrace.config;

import dev.yetpk.retrace.security.ApiKeyAuthenticationFilter;
import dev.yetpk.retrace.security.ApiKeyService;
import dev.yetpk.retrace.security.SessionAuthority;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * One API, two ways to authenticate. The UI signs in with a form login and a session; the agent
 * sends an {@code X-API-Key} header. Authentication establishes <b>who owns the data and nothing
 * else</b> — it never changes what is written, and an entry carries no trace of which credential
 * created it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Shared by password hashing and API key secret hashing. One encoder because both are secrets
     * verified by comparison and neither should be cheaper to brute-force than the other.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiKeyService apiKeyService,
                                                      ObjectMapper objectMapper) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        // The spec documents shapes, not data. One that needs a credential to read is
                        // a spec nobody reads — and it is the machine-readable half of what an agent
                        // needs to formulate a correct request.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Session only: a key must not be able to mint or revoke keys, or a leaked
                        // key would be able to issue its own replacements and outlive its revocation.
                        // Both credentials grant ROLE_USER, so the rule has to name the authority only
                        // a form login carries.
                        .requestMatchers("/api/keys/**").hasAuthority(SessionAuthority.SESSION)
                        .anyRequest().authenticated())
                // Skipped only for requests carrying X-API-Key. A browser cannot add that header
                // cross-origin without a CORS preflight, and no CORS is configured, so it cannot be
                // turned into a forgery vector.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Spring Security defers the token until something reads it, which suits a
                        // rendered form. This API renders nothing, so without eager resolution the
                        // XSRF-TOKEN cookie is never issued and the SPA has no token to send back.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(request ->
                                request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER) != null)
                        // Register and login establish a session rather than acting on one, so there
                        // is no existing authority for a forged request to borrow — and a caller with
                        // no browser cannot be asked to fetch a token before it can sign up.
                        .ignoringRequestMatchers("/api/auth/register", "/api/auth/login"))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                // Reading the token is what makes the repository write the cookie; the deferred
                // token would otherwise never materialize for a client that renders no form.
                .addFilterAfter((request, response, chain) -> {
                    Object token = request.getAttribute(CsrfToken.class.getName());
                    if (token instanceof CsrfToken csrfToken) {
                        csrfToken.getToken();
                    }
                    chain.doFilter(request, response);
                }, org.springframework.security.web.csrf.CsrfFilter.class)
                .formLogin(login -> login
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value())))
                .exceptionHandling(handling -> handling
                        // An unauthenticated API call gets a 401 rather than a redirect to a login
                        // page: the caller may well be a curl with no browser to redirect.
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // A valid key reaching /api/keys is authenticated but not permitted, which is
                        // a 403. Without this the entry point would answer 401 and tell the caller to
                        // present a credential it has already presented correctly.
                        //
                        // The status is set directly rather than with sendError, which would dispatch
                        // to the error page and run this chain a second time — that pass carries no
                        // X-API-Key header, authenticates as anonymous, and would replace this 403
                        // with a 401.
                        .accessDeniedHandler((request, response, denied) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())))
                .build();
    }
}
