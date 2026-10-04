package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The one write path, over HTTP. The pairing rules and the edge validation are the part an
 * agent-written {@code curl} is most likely to get wrong, so they are pinned here rather than left
 * to surface as a 500 in front of a caller.
 */
class EntryControllerTest extends WebTestSupport {

    private MvcResult postEntryWithFile(Project project, String entryJson, String filename, String content)
            throws Exception {
        return mockMvc.perform(postEntry(project)
                        .file(entryPart(entryJson))
                        .file(filePart(filename, content))
                        .header(API_KEY_HEADER, ownerKey))
                .andReturn();
    }

    private UUID recordArtifact(Project project, String artifactName) throws Exception {
        MvcResult result = postEntryWithFile(project,
                "{\"description\":\"Made %s.\",\"artifacts\":[{\"name\":\"%s\"}]}".formatted(artifactName,
                        artifactName),
                artifactName + ".txt", "first version");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(readJson(result).get("versions").get(0).get("artifactId").asString());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void RecordEntry_EntryWithOneFile_ShouldReturnCreatedWithOrdinalOne() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Shipped the pricing page.\","
                                + "\"note\":\"Client asked for annual plans first.\","
                                + "\"artifacts\":[{\"name\":\"pricing-page\",\"label\":\"Final\"}]}"))
                        .file(filePart("pricing.png", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8)))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Shipped the pricing page."))
                .andExpect(jsonPath("$.note").value("Client asked for annual plans first."))
                .andExpect(jsonPath("$.versions.length()").value(1))
                .andExpect(jsonPath("$.versions[0].ordinal").value(1))
                .andExpect(jsonPath("$.versions[0].artifactName").value("pricing-page"))
                .andExpect(jsonPath("$.versions[0].label").value("Final"))
                .andExpect(jsonPath("$.versions[0].filename").value("pricing.png"))
                .andExpect(jsonPath("$.versions[0].contentType").value("image/png"));
    }

    @Test
    void RecordEntry_NoFiles_ShouldReturnCreatedWithNoVersions() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Call with the client.\"}"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions.length()").value(0));
    }

    @Test
    void RecordEntry_ArtifactId_ShouldAddAVersionToTheSameArtifact() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Revised it.\",\"artifacts\":[{\"artifactId\":\"%s\"}]}"
                                .formatted(artifactId)))
                        .file(filePart("pricing-v2.txt", "second version"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions[0].artifactId").value(artifactId.toString()))
                .andExpect(jsonPath("$.versions[0].ordinal").value(2));
    }

    /** A name is a current label, not a lookup key, so reusing one makes a second artifact. */
    @Test
    void RecordEntry_NameAlreadyUsedInTheProject_ShouldCreateASecondArtifact() throws Exception {
        Project project = createProject(owner, "Website");
        UUID firstId = recordArtifact(project, "pricing-page");

        MvcResult result = postEntryWithFile(project,
                "{\"description\":\"Made another.\",\"artifacts\":[{\"name\":\"pricing-page\"}]}",
                "again.txt", "different artifact");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode version = readJson(result).get("versions").get(0);
        assertThat(UUID.fromString(version.get("artifactId").asString())).isNotEqualTo(firstId);
        assertThat(version.get("ordinal").asInt()).isEqualTo(1);
    }

    @Test
    void RecordEntry_MoreArtifactsThanFiles_ShouldReturnBadRequestNamingBothCounts() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"x\",\"artifacts\":[{\"name\":\"a\"},{\"name\":\"b\"}]}"))
                        .file(filePart("only-one.txt", "content"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.allOf(
                        Matchers.containsString("2 artifact"),
                        Matchers.containsString("1 file"))));
    }

    @Test
    void RecordEntry_MoreFilesThanArtifacts_ShouldReturnBadRequest() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"x\",\"artifacts\":[{\"name\":\"a\"}]}"))
                        .file(filePart("one.txt", "content"))
                        .file(filePart("two.txt", "content"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RecordEntry_EmptyFile_ShouldReturnBadRequestRatherThanServerError() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"x\",\"artifacts\":[{\"name\":\"a\"}]}"))
                        .file(filePart("empty.txt", ""))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RecordEntry_BlankDescription_ShouldReturnBadRequestRatherThanServerError() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"   \"}"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RecordEntry_AttachmentWithNeitherIdNorName_ShouldReturnBadRequest() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"x\",\"artifacts\":[{\"label\":\"only a label\"}]}"))
                        .file(filePart("file.txt", "content"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RecordEntry_AttachmentWithBothIdAndName_ShouldReturnBadRequest() throws Exception {
        Project project = createProject(owner, "Website");
        UUID artifactId = recordArtifact(project, "pricing-page");

        mockMvc.perform(postEntry(project)
                        .file(entryPart(("{\"description\":\"x\",\"artifacts\":"
                                + "[{\"artifactId\":\"%s\",\"name\":\"also-a-name\"}]}").formatted(artifactId)))
                        .file(filePart("file.txt", "content"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RecordEntry_MissingEntryPart_ShouldReturnBadRequest() throws Exception {
        Project project = createProject(owner, "Website");

        mockMvc.perform(postEntry(project)
                        .file(filePart("orphan.txt", "content"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void RecordEntry_ProjectOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        AppUser other = registerUser("other").user();
        Project theirs = createProject(other, "Theirs");

        mockMvc.perform(postEntry(theirs)
                        .file(entryPart("{\"description\":\"Sneaking in.\"}"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());

        assertThat(entryRepository.count()).isZero();
    }

    @Test
    void RecordEntry_ArtifactIdFromAnotherProject_ShouldReturnNotFound() throws Exception {
        Project first = createProject(owner, "First");
        Project second = createProject(owner, "Second");
        UUID artifactId = recordArtifact(first, "pricing-page");

        mockMvc.perform(postEntry(second)
                        .file(entryPart("{\"description\":\"x\",\"artifacts\":[{\"artifactId\":\"%s\"}]}"
                                .formatted(artifactId)))
                        .file(filePart("file.txt", "content"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void RecordEntry_BatchOverQuota_ShouldReturnPayloadTooLargeCarryingBothByteFigures() throws Exception {
        Project project = createProject(owner, "Website");
        // The test profile caps a project at 1KB, so a 2KB upload cannot fit.
        byte[] tooBig = new byte[2048];

        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"x\",\"artifacts\":[{\"name\":\"big\"}]}"))
                        .file(filePart("big.bin", "application/octet-stream", tooBig))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.requestedBytes").value(2048))
                .andExpect(jsonPath("$.remainingBytes").value(1024));

        assertThat(entryRepository.count()).isZero();
        assertThat(artifactRepository.count()).isZero();
    }

    /**
     * The thesis, asserted: an entry the agent records and one recorded from the UI are the same row,
     * field for field, with nothing marking which side wrote it.
     */
    @Test
    void RecordEntry_KeyAuthAndSessionAuth_ShouldProduceIndistinguishableRows() throws Exception {
        Project project = createProject(owner, "Website");
        String entryJson = "{\"description\":\"Shipped it.\",\"note\":\"Because the client asked.\","
                + "\"sessionId\":\"sess-1\",\"occurredAt\":\"2026-09-30T12:00:00Z\","
                + "\"artifacts\":[{\"name\":\"page\",\"label\":\"Final\"}]}";

        MvcResult viaKey = mockMvc.perform(postEntry(project)
                        .file(entryPart(entryJson))
                        .file(filePart("page.txt", "same bytes"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult viaSession = mockMvc.perform(postEntry(project)
                        .file(entryPart(entryJson))
                        .file(filePart("page.txt", "same bytes"))
                        .with(asSession(owner))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode keyEntry = readJson(viaKey);
        JsonNode sessionEntry = readJson(viaSession);
        // Everything but the identities generated per row has to match.
        for (String field : new String[] {"occurredAt", "description", "note", "sessionId"}) {
            assertThat(sessionEntry.get(field)).as(field).isEqualTo(keyEntry.get(field));
        }
        JsonNode keyVersion = keyEntry.get("versions").get(0);
        JsonNode sessionVersion = sessionEntry.get("versions").get(0);
        for (String field : new String[] {"artifactName", "ordinal", "label", "filename", "contentType",
                "sizeBytes"}) {
            assertThat(sessionVersion.get(field)).as(field).isEqualTo(keyVersion.get(field));
        }
        assertThat(keyEntry.get("id")).isNotEqualTo(sessionEntry.get("id"));
    }
}
