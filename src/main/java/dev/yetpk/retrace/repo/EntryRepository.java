package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.Entry;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    @Query("select e from Entry e where e.project.id = :projectId order by e.occurredAt desc, e.id desc")
    Page<Entry> findByProjectIdNewestFirst(@Param("projectId") UUID projectId, Pageable pageable);

    @Query("""
            select new dev.yetpk.retrace.repo.ProjectEntryCount(e.project.id, count(e)) from Entry e
            where e.project.id in :projectIds
            group by e.project.id""")
    List<ProjectEntryCount> countByProjectIdIn(@Param("projectIds") Collection<UUID> projectIds);

    @Query("""
            select e from Entry e
            where e.project.id = :projectId and e.occurredAt > :since
            order by e.occurredAt desc, e.id desc""")
    List<Entry> findByProjectIdOccurredAfter(@Param("projectId") UUID projectId, @Param("since") OffsetDateTime since);
}
