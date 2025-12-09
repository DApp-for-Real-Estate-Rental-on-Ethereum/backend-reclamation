package ma.fstt.reclamationservice.domain.repository;

import ma.fstt.reclamationservice.domain.entity.Reclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {
    List<Reclamation> findByBookingId(Long bookingId);
    List<Reclamation> findByComplainantId(Long complainantId);
    List<Reclamation> findByTargetUserId(Long targetUserId);
    List<Reclamation> findByComplainantIdAndComplainantRole(Long complainantId, Reclamation.ComplainantRole role);
    List<Reclamation> findByBookingIdAndComplainantId(Long bookingId, Long complainantId);
}

