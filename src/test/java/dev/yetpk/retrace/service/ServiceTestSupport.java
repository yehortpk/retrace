package dev.yetpk.retrace.service;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.repo.ArtifactRepository;
import dev.yetpk.retrace.repo.ArtifactVersionRepository;
import dev.yetpk.retrace.repo.EntryRepository;
import dev.yetpk.retrace.repo.ProjectRepository;
import dev.yetpk.retrace.service.dto.AttachmentSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared wiring for the service tests: a Spring context against the compose Postgres under the
 * {@code test} profile, plus a clean database and a fresh owner before each test.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class ServiceTestSupport {

    @Autowired
    protected ProjectService projectService;

    @Autowired
    protected TimelineService timelineService;

    @Autowired
    protected ArtifactService artifactService;

    @Autowired
    protected ArtifactStorageQuota storageQuota;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected EntryRepository entryRepository;

    @Autowired
    protected ArtifactRepository artifactRepository;

    @Autowired
    protected ArtifactVersionRepository artifactVersionRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    protected AppUser owner;

    @BeforeEach
    void resetDatabase() {
        artifactVersionRepository.deleteAllInBatch();
        artifactRepository.deleteAllInBatch();
        entryRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        owner = createUser("owner");
    }

    protected AppUser createUser(String username) {
        return appUserRepository.save(new AppUser(username, username + "@example.test", "not-a-real-hash"));
    }

    /** An attachment of {@code sizeBytes} filler bytes that creates a new artifact named {@code artifactName}. */
    protected AttachmentSpec attachmentNamed(String artifactName, String label, int sizeBytes) {
        return new AttachmentSpec(null, artifactName, label, artifactName + ".png", "image/png", sizeBytes,
                () -> new ByteArrayInputStream(new byte[sizeBytes]));
    }

    /** An attachment that adds a version to the existing artifact {@code artifactId}. */
    protected AttachmentSpec attachmentFor(UUID artifactId, String label, int sizeBytes) {
        return new AttachmentSpec(artifactId, null, label, "upload.png", "image/png", sizeBytes,
                () -> new ByteArrayInputStream(new byte[sizeBytes]));
    }

    /** An attachment carrying exactly {@code content}, so a download can be compared byte for byte. */
    protected AttachmentSpec attachmentWithContent(String artifactName, String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new AttachmentSpec(null, artifactName, null, filename, "text/plain", bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    /** Hibernate's statement counter, cleared and ready for a test to measure one call's queries. */
    protected Statistics clearAndGetStatistics() {
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
