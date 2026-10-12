package dev.yetpk.retrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    private String id;

    /** The one project this key can read and write. Its owner is the key's owner. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected ApiKey() {
    }

    public ApiKey(String id, Project project, String name, String secretHash) {
        this.id = id;
        this.project = project;
        this.name = name;
        this.secretHash = secretHash;
    }

    public String getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(OffsetDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void revoke(OffsetDateTime at) {
        this.revokedAt = at;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
