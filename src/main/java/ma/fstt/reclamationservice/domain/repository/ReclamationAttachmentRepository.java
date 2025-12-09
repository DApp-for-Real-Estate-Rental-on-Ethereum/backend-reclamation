package ma.fstt.reclamationservice.domain.repository;

import ma.fstt.reclamationservice.domain.entity.ReclamationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationAttachmentRepository extends JpaRepository<ReclamationAttachment, Long> {
    List<ReclamationAttachment> findByReclamationIdOrderByDisplayOrderAsc(Long reclamationId);
    
    long countByReclamationId(Long reclamationId);
    
    void deleteByReclamationId(Long reclamationId);
}

