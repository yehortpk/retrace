package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.Entry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    @Query("select e from Entry e where e.project.id = :projectId order by e.occurredAt desc, e.id desc")
    Page<Entry> timeline(@Param("projectId") UUID projectId, Pageable pageable);

    @Query("""
            select e from Entry e
            where e.project.id = :projectId and e.occurredAt > :since
            order by e.occurredAt desc, e.id desc""")
    List<Entry> timelineSince(@Param("projectId") UUID projectId, @Param("since") OffsetDateTime since);
}
