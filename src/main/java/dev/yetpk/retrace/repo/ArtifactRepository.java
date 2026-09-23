package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.Artifact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    @Query("select a from Artifact a where a.project.id = :projectId")
    List<Artifact> byProject(@Param("projectId") UUID projectId);

    @Query("select a from Artifact a where a.project.id = :projectId and a.name = :name")
    Optional<Artifact> byProjectAndName(@Param("projectId") UUID projectId, @Param("name") String name);

    @Query("select a from Artifact a where a.id = :id and a.project.id = :projectId")
    Optional<Artifact> byIdAndProject(@Param("id") UUID id, @Param("projectId") UUID projectId);
}
