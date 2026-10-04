package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.ApiKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    @Query("select k from ApiKey k where k.owner.id = :ownerId and k.revokedAt is null")
    List<ApiKey> findActiveByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("select k from ApiKey k where k.id = :id and k.owner.id = :ownerId")
    Optional<ApiKey> findByIdAndOwnerId(@Param("id") String id, @Param("ownerId") UUID ownerId);
}
