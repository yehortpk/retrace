package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.security.IssuedApiKey;
import dev.yetpk.retrace.service.ProjectService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** A project's keys, managed over the session: list, issue, revoke — each confined to that project. */
class ApiKeyControllerTest extends WebTestSupport {

    private JsonNode issueKeyOverHttp(Project project, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/keys", project.getId())
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void FindKeys_NewProject_ShouldListItsDefaultKeyWithoutItsValue() throws Exception {
        Project project = createProject(owner, "Website");
        String defaultKeyId = keyFor(project).substring(0, keyFor(project).indexOf('.'));

        MvcResult result = mockMvc.perform(get("/api/projects/{projectId}/keys", project.getId())
                        .with(asSession(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(defaultKeyId))
                .andExpect(jsonPath("$[0].name").value(ProjectService.DEFAULT_KEY_NAME))
                .andReturn();

        String secret = keyFor(project).substring(keyFor(project).indexOf('.') + 1);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(secret);
    }

    @Test
    void FindKeys_TwoProjects_ShouldListOnlyThisProjectsKeys() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        issueKey(other, "other desktop");

        mockMvc.perform(get("/api/projects/{projectId}/keys", mine.getId()).with(asSession(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(ProjectService.DEFAULT_KEY_NAME));
    }

    @Test
    void FindKeys_ProjectOfAnotherOwner_ShouldReturnNotFound() throws Exception {
        Project theirs = createProject(registerUser("other"), "Theirs");

        mockMvc.perform(get("/api/projects/{projectId}/keys", theirs.getId()).with(asSession(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void IssueKey_Session_ShouldReturnAKeyThatReachesThisProjectOnly() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");

        JsonNode issued = issueKeyOverHttp(mine, "desktop");

        String value = issued.get("apiKey").asString();
        assertThat(issued.get("name").asString()).isEqualTo("desktop");
        assertThat(value).startsWith(issued.get("id").asString() + ".");
        mockMvc.perform(get("/api/projects/{projectId}/timeline", mine.getId()).header(API_KEY_HEADER, value))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId()).header(API_KEY_HEADER, value))
                .andExpect(status().isNotFound());
    }

    @Test
    void IssueKey_ProjectOfAnotherOwner_ShouldReturnNotFoundAndIssueNothing() throws Exception {
        Project theirs = createProject(registerUser("other"), "Theirs");

        mockMvc.perform(post("/api/projects/{projectId}/keys", theirs.getId())
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"intruder\"}"))
                .andExpect(status().isNotFound());

        assertThat(apiKeyService.findKeys(theirs)).hasSize(1);
    }

    @Test
    void IssueKey_SessionWithoutCsrfToken_ShouldReturnForbidden() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(post("/api/projects/{projectId}/keys", project.getId())
                        .with(asSession(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"desktop\"}"))
                .andExpect(status().isForbidden());

        assertThat(apiKeyService.findKeys(project)).hasSize(1);
    }

    @Test
    void IssueKey_BlankName_ShouldReturnBadRequest() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(post("/api/projects/{projectId}/keys", project.getId())
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RevokeKey_Session_ShouldTurnTheNextKeyRequestUnauthorized() throws Exception {
        Project project = createProject(owner, "Website");
        String key = keyFor(project);
        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId()).header(API_KEY_HEADER, key))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/{projectId}/keys/{keyId}", project.getId(),
                        key.substring(0, key.indexOf('.')))
                        .with(asSession(owner))
                        .with(validCsrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId()).header(API_KEY_HEADER, key))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/{projectId}/keys", project.getId()).with(asSession(owner)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** The key id is real and the caller owns both projects — but the path names the wrong one. */
    @Test
    void RevokeKey_KeyOfAnotherProjectOfTheSameOwner_ShouldReturnNotFoundAndLeaveItWorking() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project other = createProject(owner, "Other");
        IssuedApiKey otherKey = issueKey(other, "desktop");

        mockMvc.perform(delete("/api/projects/{projectId}/keys/{keyId}", mine.getId(), otherKey.key().getId())
                        .with(asSession(owner))
                        .with(validCsrfToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/{projectId}/timeline", other.getId())
                        .header(API_KEY_HEADER, otherKey.value()))
                .andExpect(status().isOk());
    }

    @Test
    void RevokeKey_UnknownKey_ShouldReturnNotFound() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(delete("/api/projects/{projectId}/keys/{keyId}", project.getId(), "pm_doesnotexist")
                        .with(asSession(owner))
                        .with(validCsrfToken()))
                .andExpect(status().isNotFound());
    }
}
