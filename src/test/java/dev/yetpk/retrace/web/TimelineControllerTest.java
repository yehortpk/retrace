package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Entry;
import dev.yetpk.retrace.domain.Project;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class TimelineControllerTest extends WebTestSupport {

    /** Records {@code count} entries that deliberately share one timestamp. */
    private void recordEntriesAtTheSameInstant(Project project, int count, OffsetDateTime occurredAt) {
        for (int i = 0; i < count; i++) {
            entryRepository.save(new Entry(project, occurredAt, "Entry " + i, null, "sess-1"));
        }
    }

    private List<String> readIds(MvcResult result) throws Exception {
        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> ids = new ArrayList<>();
        entries.forEach(entry -> ids.add(entry.get("id").asString()));
        return ids;
    }

    private MvcResult findTimeline(Project project, String query) throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}/timeline" + query, project.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void FindTimeline_ProjectWithEntries_ShouldReturnNewestFirst() throws Exception {
        Project project = createProject(owner, "Website");
        OffsetDateTime base = OffsetDateTime.parse("2026-09-01T10:00:00Z");
        entryRepository.save(new Entry(project, base, "Oldest", null, null));
        entryRepository.save(new Entry(project, base.plusDays(1), "Middle", null, null));
        entryRepository.save(new Entry(project, base.plusDays(2), "Newest", null, null));

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Newest"))
                .andExpect(jsonPath("$[1].description").value("Middle"))
                .andExpect(jsonPath("$[2].description").value("Oldest"));
    }

    /**
     * Entries recorded in one burst share a timestamp. Without the id tiebreak in the sort, a row
     * could come back on both pages — which is what the compound sort key exists to prevent.
     */
    @Test
    void FindTimeline_EntriesSharingATimestamp_ShouldNotRepeatARowAcrossPages() throws Exception {
        Project project = createProject(owner, "Website");
        recordEntriesAtTheSameInstant(project, 10, OffsetDateTime.parse("2026-09-01T10:00:00Z"));

        List<String> firstPage = readIds(findTimeline(project, "?page=0&size=5"));
        List<String> secondPage = readIds(findTimeline(project, "?page=1&size=5"));

        assertThat(firstPage).hasSize(5);
        assertThat(secondPage).hasSize(5);
        assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
        assertThat(new java.util.HashSet<>(firstPage)).hasSize(5);
    }

    @Test
    void FindTimeline_SizeAboveTheCap_ShouldReturnNoMoreThanOneHundred() throws Exception {
        Project project = createProject(owner, "Website");
        recordEntriesAtTheSameInstant(project, 105, OffsetDateTime.parse("2026-09-01T10:00:00Z"));

        assertThat(readIds(findTimeline(project, "?size=100000"))).hasSize(100);
    }

    @Test
    void FindTimeline_NegativeSize_ShouldFallBackToTheDefaultPage() throws Exception {
        Project project = createProject(owner, "Website");
        recordEntriesAtTheSameInstant(project, 25, OffsetDateTime.parse("2026-09-01T10:00:00Z"));

        assertThat(readIds(findTimeline(project, "?size=-5"))).hasSize(20);
    }

    @Test
    void FindTimeline_NegativePage_ShouldReturnTheFirstPage() throws Exception {
        Project project = createProject(owner, "Website");
        recordEntriesAtTheSameInstant(project, 3, OffsetDateTime.parse("2026-09-01T10:00:00Z"));

        assertThat(readIds(findTimeline(project, "?page=-1"))).hasSize(3);
    }

    @Test
    void FindTimeline_Since_ShouldReturnOnlyLaterEntries() throws Exception {
        Project project = createProject(owner, "Website");
        OffsetDateTime base = OffsetDateTime.parse("2026-09-01T10:00:00Z");
        entryRepository.save(new Entry(project, base, "Before", null, null));
        entryRepository.save(new Entry(project, base.plusHours(2), "After", null, null));

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .param("since", base.plusHours(1).toString())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("After"));
    }

    @Test
    void FindTimeline_SinceAfterEverything_ShouldReturnEmpty() throws Exception {
        Project project = createProject(owner, "Website");
        entryRepository.save(new Entry(project, OffsetDateTime.parse("2026-09-01T10:00:00Z"), "Old", null, null));

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .param("since", "2027-01-01T00:00:00Z")
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void FindTimeline_EntryWithVersions_ShouldAttachThem() throws Exception {
        Project project = createProject(owner, "Website");
        mockMvc.perform(postEntry(project)
                        .file(entryPart("{\"description\":\"Shipped it.\","
                                + "\"artifacts\":[{\"name\":\"page\",\"label\":\"Final\"}]}"))
                        .file(filePart("page.txt", "bytes"))
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/{projectId}/timeline", project.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versions.length()").value(1))
                .andExpect(jsonPath("$[0].versions[0].artifactName").value("page"))
                .andExpect(jsonPath("$[0].versions[0].ordinal").value(1));
    }

    @Test
    void FindTimeline_ProjectOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        AppUser other = registerUser("other").user();
        Project theirs = createProject(other, "Theirs");
        entryRepository.save(new Entry(theirs, OffsetDateTime.now(), "Their entry", null, null));

        mockMvc.perform(get("/api/projects/{projectId}/timeline", theirs.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void FindTimeline_OtherProjectsEntries_ShouldNotLeakIn() throws Exception {
        Project mine = createProject(owner, "Mine");
        Project alsoMine = createProject(owner, "Also mine");
        entryRepository.save(new Entry(mine, OffsetDateTime.now(), "In mine", null, null));
        entryRepository.save(new Entry(alsoMine, OffsetDateTime.now(), "In the other", null, null));

        mockMvc.perform(get("/api/projects/{projectId}/timeline", mine.getId())
                        .header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("In mine"));
    }
}
