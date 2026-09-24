package dev.yetpk.retrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    /**
     * Bytes still available for new artifact versions in this project, out of
     * {@code retrace.artifacts.project-storage-limit}. Starts at the configured limit and is
     * decremented as versions are stored (incremented back if a version is removed), so it is
     * always the current remainder rather than a value derived by summing version sizes.
     * {@code null} until the application sets it, e.g. for rows predating this column.
     */
    @Column(name = "artifact_storage_remaining_bytes")
    private Long artifactStorageRemainingBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Project() {
    }

    public Project(AppUser owner, String name, String slug, String description, long artifactStorageLimitBytes) {
        this.owner = owner;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.artifactStorageRemainingBytes = artifactStorageLimitBytes;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getArtifactStorageRemainingBytes() {
        return artifactStorageRemainingBytes;
    }

    /** Whether {@code additionalBytes} more can still be stored without exceeding the quota. */
    public boolean fitsArtifactStorage(long additionalBytes) {
        requireQuotaInitialized();
        return additionalBytes <= artifactStorageRemainingBytes;
    }

    /** Reserves {@code bytes} against the remaining quota. Caller must check {@link #fitsArtifactStorage} first. */
    public void consumeArtifactStorage(long bytes) {
        requireQuotaInitialized();
        if (bytes > artifactStorageRemainingBytes) {
            throw new IllegalStateException("Not enough artifact storage quota remaining");
        }
        artifactStorageRemainingBytes -= bytes;
    }

    /** Gives back {@code bytes} to the remaining quota, e.g. when a version is deleted. */
    public void releaseArtifactStorage(long bytes) {
        requireQuotaInitialized();
        artifactStorageRemainingBytes += bytes;
    }

    private void requireQuotaInitialized() {
        if (artifactStorageRemainingBytes == null) {
            throw new IllegalStateException("artifactStorageRemainingBytes has not been initialized for this project");
        }
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
