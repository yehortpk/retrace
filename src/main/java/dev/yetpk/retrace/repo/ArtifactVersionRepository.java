package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.ArtifactVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, UUID> {

    /**
     * An artifact's versions oldest first, each with the entry that produced it. The entry is fetched
     * along with the version because the history view always shows it.
     */
    @Query("""
            select v from ArtifactVersion v join fetch v.entry
            where v.artifact.id = :artifactId
            order by v.ordinal asc""")
    List<ArtifactVersion> findByArtifactIdOldestFirst(@Param("artifactId") UUID artifactId);

    @Query("select v from ArtifactVersion v where v.artifact.id = :artifactId order by v.ordinal desc")
    List<ArtifactVersion> findByArtifactIdNewestFirst(@Param("artifactId") UUID artifactId, Limit limit);

    default Optional<ArtifactVersion> findCurrentByArtifactId(UUID artifactId) {
        return findByArtifactIdNewestFirst(artifactId, Limit.of(1)).stream().findFirst();
    }

    @Query("select coalesce(max(v.ordinal), 0) from ArtifactVersion v where v.artifact.id = :artifactId")
    int findMaxOrdinal(@Param("artifactId") UUID artifactId);

    @Query("select v from ArtifactVersion v where v.artifact.id = :artifactId and v.ordinal = :ordinal")
    Optional<ArtifactVersion> findByArtifactIdAndOrdinal(@Param("artifactId") UUID artifactId,
                                                         @Param("ordinal") int ordinal);

    @Query("select v from ArtifactVersion v where v.entry.id = :entryId")
    List<ArtifactVersion> findByEntryId(@Param("entryId") UUID entryId);

    /**
     * Versions for a whole page of entries in one query. The artifact is fetched along with each
     * version because every view of a version names its artifact: without the join, reading a
     * 20-entry page would issue a select per version to resolve the lazy association.
     */
    @Query("select v from ArtifactVersion v join fetch v.artifact where v.entry.id in :entryIds")
    List<ArtifactVersion> findByEntryIdIn(@Param("entryIds") Collection<UUID> entryIds);

    @Query("select count(v) from ArtifactVersion v where v.artifact.id = :artifactId")
    int countByArtifactId(@Param("artifactId") UUID artifactId);
}
