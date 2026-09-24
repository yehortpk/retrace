package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.ArtifactVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, UUID> {

    @Query("select v from ArtifactVersion v where v.artifact.id = :artifactId order by v.ordinal asc")
    List<ArtifactVersion> history(@Param("artifactId") UUID artifactId);

    @Query("select v from ArtifactVersion v where v.artifact.id = :artifactId order by v.ordinal desc")
    List<ArtifactVersion> newestFirst(@Param("artifactId") UUID artifactId, Limit limit);

    default Optional<ArtifactVersion> current(UUID artifactId) {
        return newestFirst(artifactId, Limit.of(1)).stream().findFirst();
    }

    @Query("select coalesce(max(v.ordinal), 0) from ArtifactVersion v where v.artifact.id = :artifactId")
    int maxOrdinal(@Param("artifactId") UUID artifactId);

    @Query("select v from ArtifactVersion v where v.entry.id = :entryId")
    List<ArtifactVersion> byEntry(@Param("entryId") UUID entryId);

    @Query("select coalesce(sum(v.sizeBytes), 0) from ArtifactVersion v where v.artifact.project.id = :projectId")
    long totalSizeByProject(@Param("projectId") UUID projectId);
}
