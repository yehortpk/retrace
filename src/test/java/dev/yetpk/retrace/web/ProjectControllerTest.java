package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ProjectControllerTest extends WebTestSupport {

    @Test
    void CreateProject_ValidRequest_ShouldReturnCreatedWithAGeneratedId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\",\"description\":\"the client site\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Website"))
                .andExpect(jsonPath("$.description").value("the client site"))
                .andExpect(jsonPath("$.entryCount").value(0))
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asString());
        assertThat(projectRepository.findById(id)).isPresent();
    }

    @Test
    void CreateProject_BlankName_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void CreateProject_SessionAuthWithCsrfToken_ShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void CreateProject_NewProject_ShouldInitializeTheStorageQuota() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asString());
        // The test profile caps a project at 1KB.
        assertThat(projectRepository.findById(id).orElseThrow().getArtifactStorageRemainingBytes())
                .isEqualTo(1024);
    }

    @Test
    void FindProjects_OwnerWithProjects_ShouldReturnThemWithEntryCounts() throws Exception {
        Project counted = createProject(owner, "Counted");
        createProject(owner, "Empty");
        entryRepository.save(new Entry(counted, OffsetDateTime.now(), "First", null, null));
        entryRepository.save(new Entry(counted, OffsetDateTime.now(), "Second", null, null));

        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Counted"))
                .andExpect(jsonPath("$[0].entryCount").value(2))
                .andExpect(jsonPath("$[1].name").value("Empty"))
                .andExpect(jsonPath("$[1].entryCount").value(0));
    }

    @Test
    void FindProjects_OwnerWithNoProjects_ShouldReturnEmpty() throws Exception {
        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void FindProjects_ProjectsOfAnotherOwner_ShouldNotBeListed() throws Exception {
        AppUser other = registerUser("other").user();
        createProject(other, "Theirs");
        createProject(owner, "Mine");

        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mine"));
    }
}
