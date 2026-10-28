package om.mtc.attendance.repository;

import om.mtc.attendance.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("""
            select r from AttendanceRecord r join fetch r.soldier s
            where r.session.id = :sessionId
            order by s.fullName asc
            """)
    List<AttendanceRecord> findBySessionWithSoldier(@Param("sessionId") Long sessionId);

    Optional<AttendanceRecord> findBySessionIdAndSoldierId(Long sessionId, Long soldierId);

    @Query("select r.soldier.id from AttendanceRecord r where r.session.id = :sessionId")
    Set<Long> findSoldierIdsBySession(@Param("sessionId") Long sessionId);

    /** [sessionId, status, count] for a set of sessions. */
    @Query("""
            select r.session.id, r.status, count(r) from AttendanceRecord r
            where r.session.id in :sessionIds group by r.session.id, r.status
            """)
    List<Object[]> countByStatusForSessions(@Param("sessionIds") Collection<Long> sessionIds);

    /** Records in a date range, with soldier and session fetched, for reports. */
    @Query("""
            select r from AttendanceRecord r join fetch r.soldier s join fetch r.session ss join fetch ss.platoon p
            where p.id in :platoonIds and ss.sessionDate between :from and :to
              and ss.status <> om.mtc.attendance.domain.SessionStatus.OPEN
            """)
    List<AttendanceRecord> findForReport(@Param("platoonIds") Collection<Long> platoonIds,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);
}
