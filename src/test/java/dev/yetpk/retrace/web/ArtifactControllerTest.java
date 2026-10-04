package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Reading and maintaining artifacts over HTTP, and the containment rule that matters most: an
 * artifact id is meaningless outside its own project, on every verb.
 */
class ArtifactControllerTest extends WebTestSupport {

    private UUID recordArtifact(Project project, String artifactName, String content) throws Exception {
        MvcResult result = mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Made %s.\",\"artifacts\":[{\"name\":\"%s\"}]}"
                                .formatted(artifactName, artifactName)))
                        .file(filePart(artifactName + ".txt", content))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("versions").get(0).get("artifactId").asString());
    }

    private void addVersion(Project project, UUID artifactId, String content) throws Exception {
        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Revised it.\",\"artifacts\":[{\"artifactId\":\"%s\"}]}"
                                .formatted(artifactId)))
                        .file(filePart("revised.txt", content))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void FindArtifacts_ProjectWithArtifacts_ShouldReturnCurrentVersionAndCount() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "v1");
        addVersion(project, artifactId, "v2");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts", project.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].versionCount").value(2))
                .andExpect(jsonPath("$[0].currentVersion.ordinal").value(2));
    }

    @Test
    void FindArtifactHistory_ArtifactWithTwoVersions_ShouldReturnBothWithTheirEntries() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "v1");
        addVersion(project, artifactId, "v2");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/{artifactId}", project.getId(), artifactId)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].ordinal").value(1))
                .andExpect(jsonPath("$.versions[0].entryDescription").value("Made pricing-page."))
                .andExpect(jsonPath("$.versions[1].ordinal").value(2))
                .andExpect(jsonPath("$.versions[1].entryDescription").value("Revised it."));
    }

    /** The payoff of identifying an artifact by a UUID assigned once: a rename touches nothing else. */
    @Test
    void RenameArtifact_ArtifactWithVersions_ShouldKeepIdVersionsAndEntryLinks() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "v1");
        addVersion(project, artifactId, "v2");

        mockMvc.perform(patch("/api/projects/{projectId}/artifacts/{artifactId}", project.getId(), artifactId)
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pricing-page-final\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(artifactId.toString()))
                .andExpect(jsonPath("$.name").value("pricing-page-final"))
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].entryDescription").value("Made pricing-page."))
                .andExpect(jsonPath("$.versions[1].entryDescription").value("Revised it."));
    }

    @Test
    void RenameArtifact_NameAlreadyUsedInTheProject_ShouldBeAccepted() throws Exception {
        Project project = createProject(owner, "Website");
        UUID first = recordArtifact(project, "pricing-page", "v1");
        UUID second = recordArtifact(project, "other-page", "v1");

        mockMvc.perform(patch("/api/projects/{projectId}/artifacts/{artifactId}", project.getId(), second)
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pricing-page\"}"))
                .andExpect(status().isOk());

        assertThat(artifactRepository.findById(first).orElseThrow().getName()).isEqualTo("pricing-page");
    }

    @Test
    void RenameArtifact_BlankName_ShouldReturnBadRequest() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "v1");

        mockMvc.perform(patch("/api/projects/{projectId}/artifacts/{artifactId}", project.getId(), artifactId)
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void DownloadVersion_StoredVersion_ShouldReturnTheBytesFilenameAndContentType() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "the original bytes");

        MvcResult result = mockMvc.perform(
                        get("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}/download",
                                project.getId(), artifactId, 1)
                                .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Content-Length", "18"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("pricing-page.txt");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("the original bytes");
    }

    @Test
    void DownloadVersion_EachVersion_ShouldReturnItsOwnBytes() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "first");
        addVersion(project, artifactId, "second");

        for (int ordinal = 1; ordinal <= 2; ordinal++) {
            MvcResult result = mockMvc.perform(
                            get("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}/download",
                                    project.getId(), artifactId, ordinal)
                                    .header(API_KEY_HEADER, ownerKey))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .isEqualTo(ordinal == 1 ? "first" : "second");
        }
    }

    @Test
    void DownloadVersion_UnknownOrdinal_ShouldReturnNotFound() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "v1");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}/download",
                        project.getId(), artifactId, 99)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void DeleteVersion_OneOfTwoVersions_ShouldLeaveTheArtifactAndReleaseQuota() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "v1");
        addVersion(project, artifactId, "v2");
        long remainingBefore = projectRepository.findById(project.getId()).orElseThrow()
                .getArtifactStorageRemainingBytes();

        mockMvc.perform(delete("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}",
                        project.getId(), artifactId, 2)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNoContent());

        assertThat(artifactRepository.findById(artifactId)).isPresent();
        assertThat(projectRepository.findById(project.getId()).orElseThrow()
                .getArtifactStorageRemainingBytes())
                .isEqualTo(remainingBefore + "v2".length());
    }

    /** An artifact with no versions is not something that happened, so it goes with its last version. */
    @Test
    void DeleteVersion_LastRemainingVersion_ShouldRemoveTheArtifact() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "only");

        mockMvc.perform(delete("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}",
                        project.getId(), artifactId, 1)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNoContent());

        assertThat(artifactRepository.findById(artifactId)).isEmpty();
        mockMvc.perform(get("/api/projects/{projectId}/artifacts/{artifactId}", project.getId(), artifactId)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void DeleteVersion_SessionAuthWithCsrfToken_ShouldReturnNoContent() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page", "only");

        mockMvc.perform(delete("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}",
                        project.getId(), artifactId, 1)
                        .with(asSession(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void FindArtifactHistory_ArtifactUnderAnotherProjectOfMine_ShouldReturnNotFound() throws Exception {
        Project first = createProject(owner, "First");
        Project second = createProject(owner, "Second");
        UUID artifactId = recordArtifact(first, "pricing-page", "v1");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/{artifactId}", second.getId(), artifactId)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void RenameArtifact_ArtifactUnderAnotherProjectOfMine_ShouldReturnNotFound() throws Exception {
        Project first = createProject(owner, "First");
        Project second = createProject(owner, "Second");
        UUID artifactId = recordArtifact(first, "pricing-page", "v1");

        mockMvc.perform(patch("/api/projects/{projectId}/artifacts/{artifactId}", second.getId(), artifactId)
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hijacked\"}"))
                .andExpect(status().isNotFound());

        assertThat(artifactRepository.findById(artifactId).orElseThrow().getName()).isEqualTo("pricing-page");
    }

    @Test
    void DownloadVersion_ArtifactUnderAnotherProjectOfMine_ShouldReturnNotFound() throws Exception {
        Project first = createProject(owner, "First");
        Project second = createProject(owner, "Second");
        UUID artifactId = recordArtifact(first, "pricing-page", "v1");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}/download",
                        second.getId(), artifactId, 1)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void DeleteVersion_ArtifactUnderAnotherProjectOfMine_ShouldReturnNotFound() throws Exception {
        Project first = createProject(owner, "First");
        Project second = createProject(owner, "Second");
        UUID artifactId = recordArtifact(first, "pricing-page", "v1");

        mockMvc.perform(delete("/api/projects/{projectId}/artifacts/{artifactId}/versions/{ordinal}",
                        second.getId(), artifactId, 1)
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());

        assertThat(artifactVersionRepository.countByArtifactId(artifactId)).isEqualTo(1);
    }

    @Test
    void FindArtifacts_ProjectOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        AppUser other = registerUser("other").user();
        Project theirs = createProject(other, "Theirs");

        mockMvc.perform(get("/api/projects/{projectId}/artifacts", theirs.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }
}
