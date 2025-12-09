package ma.fstt.reclamationservice.domain.repository;

import ma.fstt.reclamationservice.domain.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
}

