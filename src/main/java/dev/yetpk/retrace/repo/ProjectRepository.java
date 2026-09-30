package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("select p from Project p where p.owner.id = :ownerId order by p.name asc")
    List<Project> findByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("select p from Project p where p.id = :id and p.owner.id = :ownerId")
    Optional<Project> findByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
