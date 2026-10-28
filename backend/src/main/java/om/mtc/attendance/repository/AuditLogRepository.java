package om.mtc.attendance.repository;

import om.mtc.attendance.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:username is null or lower(a.username) = :username)
              and a.occurredAt between :from and :to
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(@Param("action") String action, @Param("username") String username,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Modifying
    @Query(value = "delete from audit_log where occurred_at < :before", nativeQuery = true)
    int deleteOlderThan(@Param("before") Instant before);
}
