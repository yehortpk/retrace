package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Turns "the documentation is current" into a build failure.
 *
 * <p>The spec is generated from the controller signatures, so it cannot drift silently — but it can
 * go missing, lose its security schemes, or stop describing the multipart write path if a handler is
 * refactored carelessly. An agent formulating a {@code curl} from this spec has no other source, so
 * these are the properties worth failing a build over.
 */
class OpenApiSpecTest extends WebTestSupport {

    private JsonNode readSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    @Test
    void FindApiDocs_NoCredential_ShouldBeReachable() throws Exception {
        // A spec that needs a credential to read is a spec nobody reads.
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void FindApiDocs_Spec_ShouldParseAsOpenApi() throws Exception {
        JsonNode spec = readSpec();

        assertThat(spec.get("openapi").asString()).startsWith("3.");
        assertThat(spec.get("info").get("title").asString()).isEqualTo("Retrace API");
    }

    @Test
    void FindApiDocs_Spec_ShouldListEveryEndpoint() throws Exception {
        JsonNode paths = readSpec().get("paths");

        record Endpoint(String path, String method) {
        }
        List<Endpoint> expected = List.of(
                new Endpoint("/api/auth/register", "post"),
                new Endpoint("/api/auth/me", "get"),
                new Endpoint("/api/keys", "get"),
                new Endpoint("/api/keys", "post"),
                new Endpoint("/api/keys/{keyId}", "delete"),
                new Endpoint("/api/projects", "get"),
                new Endpoint("/api/projects", "post"),
                new Endpoint("/api/projects/{projectId}/entries", "post"),
                new Endpoint("/api/projects/{projectId}/timeline", "get"),
                new Endpoint("/api/projects/{projectId}/artifacts", "get"),
                new Endpoint("/api/projects/{projectId}/artifacts/{artifactId}", "get"),
                new Endpoint("/api/projects/{projectId}/artifacts/{artifactId}", "patch"),
                new Endpoint("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}/download", "get"),
                new Endpoint("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}", "delete"));

        for (Endpoint endpoint : expected) {
            assertThat(paths.has(endpoint.path())).as("path %s", endpoint.path()).isTrue();
            assertThat(paths.get(endpoint.path()).has(endpoint.method()))
                    .as("%s %s", endpoint.method().toUpperCase(), endpoint.path())
                    .isTrue();
        }
    }

    @Test
    void FindApiDocs_Spec_ShouldDeclareTheApiKeyScheme() throws Exception {
        JsonNode schemes = readSpec().get("components").get("securitySchemes");

        assertThat(schemes.has("apiKey")).isTrue();
        JsonNode apiKey = schemes.get("apiKey");
        assertThat(apiKey.get("type").asString()).isEqualTo("apiKey");
        assertThat(apiKey.get("in").asString()).isEqualTo("header");
        assertThat(apiKey.get("name").asString()).isEqualTo("X-API-Key");
    }

    @Test
    void FindApiDocs_Spec_ShouldApplyTheApiKeySchemeByDefault() throws Exception {
        JsonNode security = readSpec().get("security");

        assertThat(security).isNotNull();
        assertThat(security.toString()).contains("apiKey");
    }

    @Test
    void FindApiDocs_RecordEntry_ShouldBeTypedAsMultipart() throws Exception {
        JsonNode post = readSpec().get("paths").get("/api/projects/{projectId}/entries").get("post");

        assertThat(post.get("requestBody").get("content").has("multipart/form-data")).isTrue();
    }

    /** The positional pairing is the rule a caller breaks, and springdoc cannot infer it. */
    @Test
    void FindApiDocs_RecordEntry_ShouldDocumentThePositionalPairing() throws Exception {
        JsonNode post = readSpec().get("paths").get("/api/projects/{projectId}/entries").get("post");

        assertThat(post.get("description").asString())
                .contains("artifacts[i]")
                .contains("files[i]");
    }

    @Test
    void FindApiDocs_RecordEntry_ShouldDocumentTheFailureStatuses() throws Exception {
        JsonNode responses = readSpec().get("paths").get("/api/projects/{projectId}/entries")
                .get("post").get("responses");

        for (String status : new String[] {"201", "400", "401", "404", "409", "413"}) {
            assertThat(responses.has(status)).as("response %s", status).isTrue();
        }
    }

    @Test
    void FindSwaggerUi_NoCredential_ShouldBeReachable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void FindProjects_NoCredential_ShouldStillRequireAuthentication() throws Exception {
        // The spec being public must not mean the API is.
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }
}
