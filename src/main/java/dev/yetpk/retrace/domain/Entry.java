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
@Table(name = "entry")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false)
    private String description;

    private String note;

    @Column(name = "session_id")
    private String sessionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Entry() {
    }

    public Entry(Project project, OffsetDateTime occurredAt, String description, String note, String sessionId) {
        this.project = project;
        this.occurredAt = occurredAt;
        this.description = description;
        this.note = note;
        this.sessionId = sessionId;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getDescription() {
        return description;
    }

    public String getNote() {
        return note;
    }

    public String getSessionId() {
        return sessionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
