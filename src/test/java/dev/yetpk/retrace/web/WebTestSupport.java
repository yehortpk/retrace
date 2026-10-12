package dev.yetpk.retrace.web;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.domain.Project;
import dev.yetpk.retrace.repo.ApiKeyRepository;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.repo.ArtifactRepository;
import dev.yetpk.retrace.repo.ArtifactVersionRepository;
import dev.yetpk.retrace.repo.EntryRepository;
import dev.yetpk.retrace.repo.ProjectRepository;
import dev.yetpk.retrace.security.ApiKeyAuthenticationFilter;
import dev.yetpk.retrace.security.ApiKeyService;
import dev.yetpk.retrace.security.IssuedApiKey;
import dev.yetpk.retrace.security.RegistrationService;
import dev.yetpk.retrace.security.SessionAuthority;
import dev.yetpk.retrace.service.ProjectService;
import dev.yetpk.retrace.service.dto.CreatedProject;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared wiring for the controller tests: the full application over {@code MockMvc} against the
 * compose Postgres under the {@code test} profile.
 *
 * <p>These run against real services rather than {@code @WebMvcTest} with mocks on purpose. The
 * invariants worth pinning at this layer — owner scoping, project containment, one write path, the
 * quota — are exactly the ones a mocked service would assert away.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class WebTestSupport {

    protected static final String API_KEY_HEADER = ApiKeyAuthenticationFilter.API_KEY_HEADER;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected ApiKeyService apiKeyService;

    @Autowired
    protected ProjectService projectService;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected ApiKeyRepository apiKeyRepository;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected EntryRepository entryRepository;

    @Autowired
    protected ArtifactRepository artifactRepository;

    @Autowired
    protected ArtifactVersionRepository artifactVersionRepository;

    protected AppUser owner;

    /** The plaintext of each project's default key, kept from creation — the only time it exists. */
    private final Map<UUID, String> projectKeys = new HashMap<>();

    @BeforeEach
    void resetDatabase() {
        artifactVersionRepository.deleteAllInBatch();
        artifactRepository.deleteAllInBatch();
        entryRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        apiKeyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();

        projectKeys.clear();
        owner = registerUser("owner");
    }

    protected AppUser registerUser(String username) {
        return registrationService.register(username, username + "@example.test", "supersecret1").user();
    }

    protected IssuedApiKey issueKey(Project project, String name) {
        return apiKeyService.issueKey(project, name);
    }

    /** Creates a project the way the API does, keeping its default key for {@link #keyFor}. */
    protected Project createProject(AppUser user, String name) {
        CreatedProject created = projectService.createProject(user, name, null);
        projectKeys.put(created.project().id(), created.key().value());
        return projectService.findProject(user, created.project().id());
    }

    /** The default key issued with {@code project}, which reaches that project and no other. */
    protected String keyFor(Project project) {
        String key = projectKeys.get(project.getId());
        if (key == null) {
            throw new IllegalStateException("No key was kept for project " + project.getId());
        }
        return key;
    }

    /** Authenticates as {@code user} through the session, the way the UI does. */
    protected RequestPostProcessor asSession(AppUser user) {
        return SecurityMockMvcRequestPostProcessors.user(user.getUsername())
                .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(SessionAuthority.SESSION));
    }

    /**
     * A CSRF token presented the way the SPA presents one: the {@code XSRF-TOKEN} cookie echoed in
     * the {@code X-XSRF-TOKEN} header, checked by the application's own cookie repository.
     *
     * <p>Spring Security's {@code csrf()} post-processor is deliberately not used. It replaces the
     * shared {@code CsrfFilter}'s repository with a session-backed one for the rest of the cached
     * context, after which every request — key requests included — creates a session, and a test that
     * a key request leaves no session behind passes or fails depending on test order.
     */
    protected static RequestPostProcessor validCsrfToken() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    /** The {@code entry} part of a write, with the content type that makes it bind as JSON. */
    protected MockMultipartFile entryPart(String json) {
        return new MockMultipartFile("entry", "entry", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    protected MockMultipartFile filePart(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("files", filename, contentType, content);
    }

    protected MockMultipartFile filePart(String filename, String content) {
        return filePart(filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    protected MockMultipartHttpServletRequestBuilder postEntry(Project project) {
        return MockMvcRequestBuilders.multipart("/api/projects/{projectId}/entries", project.getId());
    }
}
