package dev.yetpk.retrace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStoreTest {

    @TempDir
    Path storageRoot;

    private LocalFileStore fileStore;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        fileStore = new LocalFileStore(storageRoot.toString());
        projectId = UUID.randomUUID();
    }

    @Test
    void byteRoundTrip() throws IOException {
        byte[] content = "hello, retrace".getBytes(StandardCharsets.UTF_8);

        UUID key = fileStore.storeContent(projectId, new ByteArrayInputStream(content));

        try (InputStream readBack = fileStore.retrieveContent(projectId, key)) {
            assertThat(readBack.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void generatesAUniqueKeyPerCall() {
        Set<UUID> keys = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            keys.add(fileStore.storeContent(projectId, new ByteArrayInputStream(("content " + i).getBytes(StandardCharsets.UTF_8))));
        }

        assertThat(keys).hasSize(20);
    }

    @Test
    void storesUnderProjectIdAndArtifactsSubdirectory() {
        UUID key = fileStore.storeContent(projectId, new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));

        Path expected = storageRoot.toAbsolutePath().normalize()
                .resolve(projectId.toString())
                .resolve("artifacts")
                .resolve(key.toString());
        assertThat(expected).exists();
    }

    @Test
    void leavesNoTempFilesBehindAfterASuccessfulStore() throws IOException {
        fileStore.storeContent(projectId, new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));

        try (var files = Files.list(storageRoot)) {
            boolean anyTempFile = files.anyMatch(p -> p.getFileName().toString().startsWith("upload-"));
            assertThat(anyTempFile).isFalse();
        }
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> fileStore.storeContent(projectId, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
