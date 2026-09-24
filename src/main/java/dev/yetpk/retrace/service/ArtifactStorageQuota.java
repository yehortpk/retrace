package dev.yetpk.retrace.service;

import dev.yetpk.retrace.domain.Project;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Caps the cumulative size of all artifact versions within a single project.
 * The remaining quota is tracked directly on {@link Project}; this service only knows the
 * configured limit that a new project's remainder is initialized to.
 */
@Component
public class ArtifactStorageQuota {

    private final long limitBytes;

    public ArtifactStorageQuota(@Value("${retrace.artifacts.project-storage-limit}") DataSize limit) {
        this.limitBytes = limit.toBytes();
    }

    public long limitBytes() {
        return limitBytes;
    }

    /** Whether storing {@code additionalBytes} more in the project stays within its remaining quota. */
    public boolean fits(Project project, long additionalBytes) {
        return project.fitsArtifactStorage(additionalBytes);
    }
}
