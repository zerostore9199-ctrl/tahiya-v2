package om.mtc.attendance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One enrolled face template. Only the encrypted embedding is stored — never the photo. */
@Entity
@Table(name = "face_embedding")
@Getter
@Setter
@NoArgsConstructor
public class FaceEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "soldier_id")
    private Soldier soldier;

    /** AES-256-GCM: 12-byte nonce || ciphertext+tag of 512 float32 values. */
    @Column(name = "embedding_enc", nullable = false)
    private byte[] embeddingEnc;

    @Column(name = "model_name", nullable = false, length = 50)
    private String modelName;

    @Column(name = "det_score", nullable = false)
    private float detScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
