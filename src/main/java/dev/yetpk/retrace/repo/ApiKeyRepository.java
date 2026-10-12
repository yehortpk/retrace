package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.ApiKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    @Query("select k from ApiKey k where k.project.id = :projectId and k.revokedAt is null order by k.createdAt asc")
    List<ApiKey> findActiveByProjectId(@Param("projectId") UUID projectId);

    @Query("select k from ApiKey k where k.id = :id and k.project.id = :projectId")
    Optional<ApiKey> findByIdAndProjectId(@Param("id") String id, @Param("projectId") UUID projectId);

    /**
     * The key with its project and that project's owner, in one query — what authentication needs to
     * know who is calling and which project they are confined to.
     */
    @Query("select k from ApiKey k join fetch k.project p join fetch p.owner where k.id = :id")
    Optional<ApiKey> findWithProjectAndOwner(@Param("id") String id);
}
