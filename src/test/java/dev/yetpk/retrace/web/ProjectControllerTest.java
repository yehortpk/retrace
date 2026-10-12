package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.service.ProjectService;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class ProjectControllerTest extends WebTestSupport {

    @Autowired
    private PasswordEncoder passwordEncoder;

    private JsonNode createProjectOverHttp(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void CreateProject_ValidRequest_ShouldReturnCreatedWithAGeneratedId() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\",\"description\":\"the client site\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.name").value("Website"))
                .andExpect(jsonPath("$.project.description").value("the client site"))
                .andExpect(jsonPath("$.project.entryCount").value(0));

        assertThat(projectRepository.findAll()).extracting(Project::getName).containsExactly("Website");
    }

    @Test
    void CreateProject_BlankName_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .with(validCsrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(projectRepository.count()).isZero();
        assertThat(apiKeyRepository.count()).isZero();
    }

    @Test
    void CreateProject_NewProject_ShouldInitializeTheStorageQuota() throws Exception {
        UUID id = UUID.fromString(createProjectOverHttp("{\"name\":\"Website\"}").get("project").get("id").asString());

        // The test profile caps a project at 1KB.
        assertThat(projectRepository.findById(id).orElseThrow().getArtifactStorageRemainingBytes())
                .isEqualTo(1024);
    }

    @Test
    void CreateProject_NewProject_ShouldReturnItsFirstKeyOnce() throws Exception {
        JsonNode created = createProjectOverHttp("{\"name\":\"Website\"}");

        UUID projectId = UUID.fromString(created.get("project").get("id").asString());
        JsonNode key = created.get("key");
        assertThat(key.get("name").asString()).isEqualTo(ProjectService.DEFAULT_KEY_NAME);
        assertThat(key.get("apiKey").asString()).startsWith(key.get("id").asString() + ".");

        List<ApiKey> stored = apiKeyRepository.findActiveByProjectId(projectId);
        assertThat(stored).extracting(ApiKey::getId).containsExactly(key.get("id").asString());
        String secret = key.get("apiKey").asString().substring(key.get("apiKey").asString().indexOf('.') + 1);
        assertThat(passwordEncoder.matches(secret, stored.getFirst().getSecretHash())).isTrue();
    }

    /** The point of creating the key with the project: it is immediately usable, on that project. */
    @Test
    void CreateProject_ReturnedKey_ShouldReachTheNewProject() throws Exception {
        JsonNode created = createProjectOverHttp("{\"name\":\"Website\"}");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", created.get("project").get("id").asString())
                        .header(API_KEY_HEADER, created.get("key").get("apiKey").asString()))
                .andExpect(status().isOk());
    }

    @Test
    void CreateProject_ReturnedKey_ShouldNotReachAnotherProjectOfTheSameOwner() throws Exception {
        Project existing = createProject(owner, "Existing");
        JsonNode created = createProjectOverHttp("{\"name\":\"Website\"}");

        mockMvc.perform(get("/api/projects/{projectId}/timeline", existing.getId())
                        .header(API_KEY_HEADER, created.get("key").get("apiKey").asString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void FindProjects_OwnerWithProjects_ShouldReturnThemWithEntryCounts() throws Exception {
        Project counted = createProject(owner, "Counted");
        createProject(owner, "Empty");
        entryRepository.save(new Entry(counted, OffsetDateTime.now(), "First", null, null));
        entryRepository.save(new Entry(counted, OffsetDateTime.now(), "Second", null, null));

        mockMvc.perform(get("/api/projects").with(asSession(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Counted"))
                .andExpect(jsonPath("$[0].entryCount").value(2))
                .andExpect(jsonPath("$[1].name").value("Empty"))
                .andExpect(jsonPath("$[1].entryCount").value(0));
    }

    @Test
    void FindProjects_OwnerWithNoProjects_ShouldReturnEmpty() throws Exception {
        mockMvc.perform(get("/api/projects").with(asSession(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void FindProjects_ProjectsOfAnotherOwner_ShouldNotBeListed() throws Exception {
        AppUser other = registerUser("other");
        createProject(other, "Theirs");
        createProject(owner, "Mine");

        mockMvc.perform(get("/api/projects").with(asSession(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mine"));
    }
}
