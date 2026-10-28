package om.mtc.attendance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Encrypted profile picture of a soldier or a user (exactly one of the two owner ids is set). */
@Entity
@Table(name = "profile_photo")
@Getter
@Setter
@NoArgsConstructor
public class ProfilePhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "soldier_id")
    private Long soldierId;

    @Column(name = "user_id")
    private Long userId;

    /** AES-256-GCM: 12-byte nonce || ciphertext+tag of the JPEG bytes. */
    @Column(name = "content_enc", nullable = false)
    private byte[] contentEnc;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType = "image/jpeg";

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
