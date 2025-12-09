package ma.fstt.reclamationservice.core.service;

import lombok.RequiredArgsConstructor;
import ma.fstt.reclamationservice.domain.entity.UserAccount;
import ma.fstt.reclamationservice.domain.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserScoreService {

    private final UserAccountRepository userAccountRepository;
    
    public UserAccountRepository getUserAccountRepository() {
        return userAccountRepository;
    }

    @Transactional
    public void deductPenaltyPoints(Long userId, Integer penaltyPoints) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        
        if (user == null) {
            throw new RuntimeException("User not found in database. User ID: " + userId);
        }

        int currentScore = user.getScore() != null ? user.getScore() : 100;
        
        if (penaltyPoints <= 0) {
            return;
        }
        
        int newScore = Math.max(0, currentScore - penaltyPoints);
        user.setScore(newScore);

        checkAndApplySuspension(user);

        userAccountRepository.save(user);
        userAccountRepository.flush();
    }

    private void checkAndApplySuspension(UserAccount user) {
        int score = user.getScore() != null ? user.getScore() : 100;
        int pointsDeducted = 100 - score;

        if (score <= 74) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Score too low (≤74) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(null);
        } else if (score <= 79) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Low score (75-79) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(LocalDateTime.now().plusDays(60));
        } else if (score <= 84) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Low score (80-84) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(LocalDateTime.now().plusDays(30));
        } else if (score <= 89) {
            user.setIsSuspended(true);
            user.setSuspensionReason("Moderate score (85-89) - " + pointsDeducted + " penalty points deducted");
            user.setSuspensionUntil(LocalDateTime.now().plusDays(7));
        } else if (score > 89 && user.getIsSuspended() != null && user.getIsSuspended()) {
            if (user.getSuspensionUntil() != null && LocalDateTime.now().isAfter(user.getSuspensionUntil())) {
                user.setIsSuspended(false);
                user.setSuspensionReason(null);
                user.setSuspensionUntil(null);
            }
        }
    }
}

