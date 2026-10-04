package dev.yetpk.retrace.config;

import dev.yetpk.retrace.security.ApiKeyAuthenticationFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for its two readers that cannot read the source: the React app, and — more to the
 * point — an agent formulating a {@code curl} from a natural-language instruction.
 *
 * <p>The spec is <b>generated from the controller signatures</b>, never hand-maintained, so it cannot
 * drift from the handlers the way a checked-in {@code openapi.yaml} would. This bean supplies only
 * the part no annotation on a handler can: the document's identity and its security schemes, so the
 * spec states how to authenticate instead of leaving a caller to guess.
 */
@Configuration
public class OpenApiConfig {

    static final String API_KEY_SCHEME = "apiKey";
    static final String SESSION_SCHEME = "session";

    @Bean
    public OpenAPI retraceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Retrace API")
                        .version("0.1.0")
                        .description("""
                                A chronological history layer for a project: what happened, what changed, \
                                what was produced, and why.

                                The bet is that **the agent does the remembering**. An entry recorded by an \
                                agent and one recorded by hand are not merely similar but identical — same \
                                endpoint, same structure, same stored row, with nothing recording which \
                                side wrote it. That is why there is exactly one write path, \
                                `POST /api/projects/{projectId}/entries`.

                                Two ways to authenticate, both establishing only *who owns the data*: an \
                                `X-API-Key` header (the agent) or a session cookie from \
                                `POST /api/auth/login` (the UI). Key management under `/api/keys` is \
                                reachable by session only, so a key cannot mint or revoke keys."""))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(ApiKeyAuthenticationFilter.API_KEY_HEADER)
                                .description("A key issued at registration or from /api/keys, sent as "
                                        + "`<id>.<secret>`. Only its bcrypt hash is stored, so a lost key "
                                        + "cannot be recovered — issue a new one."))
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("The session established by POST /api/auth/login. Required "
                                        + "for /api/keys, accepted everywhere else.")))
                // The default requirement is the agent's path, which is also what makes Swagger UI's
                // "Authorize" box able to execute every endpoint with a real key.
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
