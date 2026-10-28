package om.mtc.attendance.repository;

import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    @Query("select u from AppUser u left join fetch u.platoon where u.id = :id")
    Optional<AppUser> findWithPlatoon(@Param("id") Long id);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndEnabledTrue(Role role);

    List<AppUser> findByRoleAndPlatoon_IdAndEnabledTrue(Role role, Long platoonId);

    @Query("""
            select u from AppUser u left join u.platoon p
            where (:role is null or u.role = :role)
              and (:platoonId is null or p.id = :platoonId)
              and (:q is null or lower(u.username) like :q or lower(u.fullName) like :q)
            """)
    Page<AppUser> search(@Param("role") Role role, @Param("platoonId") Long platoonId,
                         @Param("q") String q, Pageable pageable);
}
