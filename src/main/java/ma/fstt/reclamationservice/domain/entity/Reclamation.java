package ma.fstt.reclamationservice.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reclamations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reclamation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "complainant_id", nullable = false)
    private Long complainantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "complainant_role", nullable = false, length = 10)
    private ComplainantRole complainantRole;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ReclamationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReclamationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private Severity severity;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "penalty_points")
    private Integer penaltyPoints;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ReclamationStatus.OPEN;
        }
        if (severity == null) {
            severity = Severity.LOW;
        }
        if (refundAmount == null) {
            refundAmount = BigDecimal.ZERO;
        }
        if (penaltyPoints == null) {
            penaltyPoints = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (status == ReclamationStatus.RESOLVED || status == ReclamationStatus.REJECTED) {
            if (resolvedAt == null) {
                resolvedAt = LocalDateTime.now();
            }
        }
    }

    public enum ComplainantRole {
        GUEST,
        HOST
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
