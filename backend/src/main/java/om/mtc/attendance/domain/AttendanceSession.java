package om.mtc.attendance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_session")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platoon_id")
    private Platoon platoon;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 30)
    private SessionType sessionType;

    @Column(length = 120)
    private String title;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** Optional scheduled start; faces recognised after start + grace are marked LATE. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by")
    private AppUser openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private AppUser closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Optimistic locking: two devices closing the same session cannot both win. */
    @Version
    private long version;

    public Long getPlatoonId() {
        return platoon == null ? null : platoon.getId();
    }
}
