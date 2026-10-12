package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.IssuedApiKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Pins how the two credentials behave at the HTTP edge — the part of the design that is a set of
 * deliberate choices rather than framework defaults.
 *
 * <p>Since keys belong to projects, the rules are: a session reaches every project its user owns; a
 * key reaches <b>its own project and nothing else</b>. Another project of the same owner answers 404,
 * exactly as a project that does not exist would, and the session-only paths (listing and creating
 * projects, key management) answer 403.
 */
class ApiKeySecurityTest extends WebTestSupport {

    private MockHttpSession signIn(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .param("username", username)
                        .param("password", "supersecret1"))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UUID recordArtifact(Project project, String artifactName) throws Exception {
        MvcResult result = mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Made %s.\",\"artifacts\":[{\"name\":\"%s\"}]}"
                                .formatted(artifactName, artifactName)))
                        .file(filePart(artifactName + ".txt", "content"))
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("versions").get(0).get("artifactId").asString());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // --- Credentials -------------------------------------------------------------------------------

    @Test
    void FindTimeline_KeyOfItsOwnProject_ShouldReturnOk() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isOk());
    }

    @Test
    void FindTimeline_NoCredential_ShouldReturnUnauthorized() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindTimeline_UnknownKey_ShouldReturnUnauthorized() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, "pm_doesnotexist.somesecret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindTimeline_MalformedKey_ShouldReturnUnauthorized() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, "no-separator"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindTimeline_RevokedKey_ShouldReturnUnauthorized() throws Exception {
        Project project = createProject(owner, "Website");
        IssuedApiKey issued = issueKey(project, "temporary");
        apiKeyService.revokeKey(project, issued.key().getId());

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, issued.value()))
                .andExpect(status().isUnauthorized());
    }

    /** Revoking one key disconnects exactly one repo: the project's other keys keep working. */
    @Test
    void FindTimeline_OtherKeyOfARevokedKeysProject_ShouldStillReturnOk() throws Exception {
        Project project = createProject(owner, "Website");
        IssuedApiKey revoked = issueKey(project, "old laptop");
        apiKeyService.revokeKey(project, revoked.key().getId());

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isOk());
    }

    @Test
    void FindTimeline_KeyOfADeletedProject_ShouldReturnUnauthorized() throws Exception {
        Project project = createProject(owner, "Website");
        String key = keyFor(project);
        projectRepository.deleteById(project.getId());

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindTimeline_KeyAuthentication_ShouldCreateNoSession() throws Exception {
        Project project = createProject(owner, "Website");

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void RecordEntry_KeyAuthenticationWithoutCsrfToken_ShouldReturnCreated() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Shipped it.\"}"))
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isCreated());
    }

    @Test
    void RecordEntry_SessionWithoutCsrfToken_ShouldReturnForbidden() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Shipped it.\"}"))
                        .with(asSession(owner)))
                .andExpect(status().isForbidden());

        assertThat(entryRepository.count()).isZero();
    }

    /**
     * A request that presents a credential and gets it wrong is a mistake its caller needs to see,
     * not an invitation to act as whoever the cookie belongs to.
     */
    @Test
    void FindProjects_InvalidKeyWithValidSession_ShouldReturnUnauthorizedRatherThanFallBack() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .with(asSession(owner))
                        .header(API_KEY_HEADER, "pm_doesnotexist.somesecret"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A key presented alongside a session is what the request is judged by — so the session's wider
     * reach does not widen the key's.
     */
    @Test
    void FindTimeline_ValidKeyWithSessionOnAnotherProject_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .with(asSession(owner))
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
    }

    @Test
    void Login_CorrectPassword_ShouldEstablishASession() throws Exception {
        registerUser("ada");

        MockHttpSession session = signIn("ada");

        mockMvc.perform(get("/api/projects").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void Login_WrongPassword_ShouldReturnUnauthorized() throws Exception {
        registerUser("ada");

        mockMvc.perform(post("/api/auth/login")
                        .param("username", "ada")
                        .param("password", "not-the-password"))
                .andExpect(status().isUnauthorized());
    }

    // --- Registration ------------------------------------------------------------------------------

    @Test
    void Register_TakenUsername_ShouldReturnConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"email\":\"new@example.test\","
                                + "\"password\":\"supersecret1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void Register_ShortPassword_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"email\":\"ada@example.test\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void Register_NewUser_ShouldIssueNoKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"email\":\"ada@example.test\","
                                + "\"password\":\"supersecret1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ada"))
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.has("apiKey")).isFalse();
        assertThat(body.has("apiKeyId")).isFalse();
        assertThat(apiKeyRepository.count()).isZero();
    }

    // --- /api/auth/me ------------------------------------------------------------------------------

    /** How the plugin, configured with nothing but a key, learns which project it writes to. */
    @Test
    void FindCurrentUser_KeyAuthentication_ShouldCarryTheKeysProjectId() throws Exception {
        createProject(owner, "First");
        Project second = createProject(owner, "Second");

        mockMvc.perform(get("/api/auth/me").header(API_KEY_HEADER, keyFor(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.projectId").value(second.getId().toString()));
    }

    @Test
    void FindCurrentUser_SecondKeyOfAProject_ShouldCarryThatProjectsId() throws Exception {
        Project project = createProject(owner, "Website");
        IssuedApiKey second = issueKey(project, "desktop");

        mockMvc.perform(get("/api/auth/me").header(API_KEY_HEADER, second.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId().toString()));
    }

    @Test
    void FindCurrentUser_SessionAuthentication_ShouldCarryNoProjectId() throws Exception {
        createProject(owner, "Website");

        MvcResult result = mockMvc.perform(get("/api/auth/me").with(asSession(owner)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode projectId = readJson(result).get("projectId");
        assertThat(projectId == null || projectId.isNull()).isTrue();
    }

    @Test
    void FindCurrentUser_KeyAndSession_ShouldIdentifyTheSameOwner() throws Exception {
        Project project = createProject(owner, "Website");

        JsonNode viaKey = readJson(mockMvc.perform(get("/api/auth/me").header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode viaSession = readJson(mockMvc.perform(get("/api/auth/me").with(asSession(owner)))
                .andExpect(status().isOk())
                .andReturn());

        for (String field : new String[] {"id", "username", "email"}) {
            assertThat(viaKey.get(field)).as(field).isEqualTo(viaSession.get(field));
        }
    }

    // --- A key is confined to its project ----------------------------------------------------------

    @Test
    void FindTimeline_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .param("since", "2000-01-01T00:00:00Z")
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
    }

    @Test
    void RecordEntry_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFoundAndRecordNothing() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");

        mockMvc.perform(postEntry(other)
                        .file(entryPart("{\"description\":\"Wrong project.\",\"artifacts\":[{\"name\":\"page\"}]}"))
                        .file(filePart("page.txt", "content"))
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());

        assertThat(entryRepository.count()).isZero();
        assertThat(artifactRepository.count()).isZero();
        assertThat(projectRepository.findById(other.getId()).orElseThrow().getArtifactStorageRemainingBytes())
                .isEqualTo(1024);
    }

    @Test
    void FindArtifacts_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        recordArtifact(other, "their-page");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts", other.getId())
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
    }

    @Test
    void FindArtifactHistory_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        UUID artifactId = recordArtifact(other, "their-page");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/{artifactId}", other.getId(), artifactId)
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
    }

    @Test
    void RenameArtifact_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFoundAndKeepTheName() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        UUID artifactId = recordArtifact(other, "their-page");

        mockMvc.perform(patch("/api/projects/{projectId}/artifacts/{artifactId}", other.getId(), artifactId)
                        .header(API_KEY_HEADER, keyFor(mine))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hijacked\"}"))
                .andExpect(status().isNotFound());

        assertThat(artifactRepository.findById(artifactId).orElseThrow().getName()).isEqualTo("their-page");
    }

    @Test
    void DownloadVersion_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        UUID artifactId = recordArtifact(other, "their-page");

        MvcResult result = mockMvc.perform(
                        get("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}/download",
                                other.getId(), artifactId, 1)
                                .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("content");
    }

    @Test
    void DeleteVersion_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnNotFoundAndKeepTheVersion() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        UUID artifactId = recordArtifact(other, "their-page");

        mockMvc.perform(delete("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}",
                        other.getId(), artifactId, 1)
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());

        assertThat(artifactVersionRepository.countByArtifactId(artifactId)).isEqualTo(1);
    }

    @Test
    void FindTimeline_KeyOnAProjectOfAnotherOwner_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project theirs = createProject(registerUser("other"), "Theirs");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", theirs.getId())
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
    }

    @Test
    void FindTimeline_KeyOnAnUnknownProjectId_ShouldReturnNotFound() throws Exception {
        Project mine = createProject(owner, "Mine");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", UUID.randomUUID())
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound());
    }

    /**
     * A foreign project id has to look the same to a key as it does to a stranger, or a key could be
     * used to tell which of its owner's project ids exist.
     */
    @Test
    void FindTimeline_KeyOnAnotherProjectOfTheSameOwner_ShouldAnswerExactlyAsToAStranger() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        AppUser stranger = registerUser("stranger");

        String toTheKey = mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String toTheStranger = mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .with(asSession(stranger)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(toTheKey).isEqualTo(toTheStranger);
    }

    @Test
    void FindTimeline_KeyIssuedLaterForAProject_ShouldBeConfinedToItToo() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        IssuedApiKey second = issueKey(mine, "desktop");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", mine.getId())
                        .header(API_KEY_HEADER, second.value()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .header(API_KEY_HEADER, second.value()))
                .andExpect(status().isNotFound());
    }

    /** Confinement is a property of keys; a session still reaches every project its user owns. */
    @Test
    void FindTimeline_SessionOnEachOfTheOwnersProjects_ShouldReturnOk() throws Exception {
        Project first = createProject(owner, "First");
        Project second = createProject(owner, "Second");

        for (Project project : new Project[] {first, second}) {
            mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId()).with(asSession(owner)))
                    .andExpect(status().isOk());
        }
    }

    // --- Session-only paths ------------------------------------------------------------------------

    @Test
    void FindProjects_KeyAuthentication_ShouldReturnForbidden() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isForbidden());
    }

    /**
     * Only the exact path is session-only; a trailing slash must not turn into a way around it. It is
     * not mapped to the project list, so it can only fail.
     */
    @Test
    void FindProjects_KeyAuthenticationWithTrailingSlash_ShouldNotListProjects() throws Exception {
        Project project = createProject(owner, "Website");

        MvcResult result = mockMvc.perform(get("/api/projects/").header(API_KEY_HEADER, keyFor(project)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Website");
    }

    @Test
    void CreateProject_KeyAuthentication_ShouldReturnForbiddenAndCreateNothing() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(post("/api/projects")
                        .header(API_KEY_HEADER, keyFor(project))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spawned\"}"))
                .andExpect(status().isForbidden());

        assertThat(projectRepository.count()).isEqualTo(1);
        assertThat(apiKeyRepository.count()).isEqualTo(1);
    }

    @Test
    void CreateProject_SessionWithoutCsrfToken_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isForbidden());

        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void FindKeys_KeyOnItsOwnProject_ShouldReturnForbidden() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects/{projectId}/keys", project.getId())
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isForbidden());
    }

    @Test
    void IssueKey_KeyOnItsOwnProject_ShouldReturnForbiddenAndIssueNothing() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(post("/api/projects/{projectId}/keys", project.getId())
                        .header(API_KEY_HEADER, keyFor(project))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"replacement\"}"))
                .andExpect(status().isForbidden());

        assertThat(apiKeyRepository.count()).isEqualTo(1);
    }

    /** A leaked key must not be able to revoke the keys that would replace it, or itself. */
    @Test
    void RevokeKey_KeyOnItsOwnProject_ShouldReturnForbiddenAndRevokeNothing() throws Exception {
        Project project = createProject(owner, "Website");
        IssuedApiKey other = issueKey(project, "desktop");

        mockMvc.perform(delete("/api/projects/{projectId}/keys/{keyId}", project.getId(), other.key().getId())
                        .header(API_KEY_HEADER, keyFor(project)))
                .andExpect(status().isForbidden());

        assertThat(apiKeyService.findKeys(project)).hasSize(2);
    }

    @Test
    void FindKeys_KeyOnAnotherProjectOfTheSameOwner_ShouldReturnForbidden() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");

        mockMvc.perform(get("/api/projects/{projectId}/keys", other.getId())
                        .header(API_KEY_HEADER, keyFor(mine)))
                .andExpect(status().isForbidden());
    }

    @Test
    void FindKeys_KeyAuthenticationWithTrailingSlash_ShouldNotListKeys() throws Exception {
        Project project = createProject(owner, "Website");

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/keys/", project.getId())
                        .header(API_KEY_HEADER, keyFor(project)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("pm_");
    }

    @Test
    void FindKeys_SessionAuthentication_ShouldReturnOk() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(get("/api/projects/{projectId}/keys", project.getId()).with(asSession(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void FindKeys_RealSessionFromLogin_ShouldReturnOk() throws Exception {
        Project project = createProject(owner, "Website");
        MockHttpSession session = signIn("owner");

        mockMvc.perform(get("/api/projects/{projectId}/keys", project.getId()).session(session))
                .andExpect(status().isOk());
    }

    @Test
    void CreateProject_CsrfTokenWithSession_ShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isCreated());
    }
}
