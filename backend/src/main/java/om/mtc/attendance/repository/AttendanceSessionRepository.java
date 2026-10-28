package om.mtc.attendance.repository;

import om.mtc.attendance.domain.AttendanceSession;
import om.mtc.attendance.domain.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    Optional<AttendanceSession> findFirstByPlatoon_IdAndStatus(Long platoonId, SessionStatus status);

    @Query(value = """
            select s from AttendanceSession s join fetch s.platoon p join fetch s.openedBy
            where p.id in :platoonIds
              and (:status is null or s.status = :status)
              and s.sessionDate between :from and :to
            order by s.openedAt desc
            """, countQuery = """
            select count(s) from AttendanceSession s
            where s.platoon.id in :platoonIds
              and (:status is null or s.status = :status)
              and s.sessionDate between :from and :to
            """)
    Page<AttendanceSession> search(@Param("platoonIds") Collection<Long> platoonIds,
                                   @Param("status") SessionStatus status,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to,
                                   Pageable pageable);

    @Query("""
            select s from AttendanceSession s join fetch s.platoon p
            where p.id in :platoonIds and s.sessionDate between :from and :to
            order by s.sessionDate asc, s.openedAt asc
            """)
    List<AttendanceSession> findInRange(@Param("platoonIds") Collection<Long> platoonIds,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select s from AttendanceSession s join fetch s.platoon p
            where p.id in :platoonIds and s.status = om.mtc.attendance.domain.SessionStatus.CLOSED
            order by s.closedAt asc
            """)
    List<AttendanceSession> findPendingApproval(@Param("platoonIds") Collection<Long> platoonIds);
}
