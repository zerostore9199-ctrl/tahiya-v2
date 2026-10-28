package om.mtc.attendance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "soldier")
@Getter
@Setter
@NoArgsConstructor
public class Soldier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "military_number", nullable = false, unique = true, length = 30)
    private String militaryNumber;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "military_rank", nullable = false, length = 50)
    private String militaryRank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platoon_id")
    private Platoon platoon;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "biometric_consent", nullable = false)
    private boolean biometricConsent;

    @Column(name = "consent_at")
    private Instant consentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_by")
    private AppUser consentBy;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    /** Version stamp of the stored profile photo (null = no photo). The image itself lives in profile_photo. */
    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getPlatoonId() {
        return platoon == null ? null : platoon.getId();
    }
}
