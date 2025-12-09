package ma.fstt.reclamationservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "personal_id", length = 80, nullable = true)
    private String personalId;

    @Column(name = "birthday", nullable = true)
    private LocalDate birthday;

    @Column(name = "phone_number", nullable = true)
    private Long phoneNumber;

    @Column(name = "wallet_address", columnDefinition = "TEXT", nullable = true)
    private String walletAddress;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "score", nullable = false, columnDefinition = "INTEGER DEFAULT 100")
    private Integer score = 100;

    @Column(name = "penalty_points", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer penaltyPoints = 0;

    @Column(name = "is_suspended", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isSuspended = false;

    @Column(name = "suspension_reason", columnDefinition = "TEXT", nullable = true)
    private String suspensionReason;

    @Column(name = "suspension_until", nullable = true)
    private LocalDateTime suspensionUntil;
}

