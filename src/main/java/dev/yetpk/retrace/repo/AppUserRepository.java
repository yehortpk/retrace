package dev.yetpk.retrace.repo;

import dev.yetpk.retrace.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findByUsername(@Param("username") String username);

    @Query("select u from AppUser u where u.email = :email")
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from AppUser u where u.username = :username")
    boolean isUsernameTaken(@Param("username") String username);

    @Query("select count(u) > 0 from AppUser u where u.email = :email")
    boolean isEmailTaken(@Param("email") String email);
}
