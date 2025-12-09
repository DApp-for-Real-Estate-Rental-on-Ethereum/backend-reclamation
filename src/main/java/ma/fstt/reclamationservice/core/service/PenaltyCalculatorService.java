package ma.fstt.reclamationservice.core.service;

import ma.fstt.reclamationservice.domain.entity.Reclamation;
import ma.fstt.reclamationservice.domain.entity.Reclamation.ComplainantRole;
import ma.fstt.reclamationservice.domain.entity.Reclamation.Severity;
import ma.fstt.reclamationservice.domain.entity.ReclamationType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PenaltyCalculatorService {

    private static final BigDecimal PLATFORM_FEE_PERCENT = new BigDecimal("0.10"); // 10%

    public PenaltyCalculationResult calculatePenalty(
            Reclamation reclamation,
            BigDecimal totalRentAmount,
            BigDecimal totalDepositAmount
    ) {
        ReclamationType type = reclamation.getType();
        Severity severity = reclamation.getSeverity();
        ComplainantRole role = reclamation.getComplainantRole();

        BigDecimal refundAmount = BigDecimal.ZERO;
        Integer penaltyPoints = 0;

        if (role == ComplainantRole.GUEST) {
            switch (type) {
                case ACCESS_ISSUE:
                    refundAmount = totalRentAmount.add(totalDepositAmount);
                    penaltyPoints = 10;
                    break;
                case NOT_AS_DESCRIBED:
                    refundAmount = totalRentAmount.add(totalDepositAmount);
                    penaltyPoints = 10;
                    break;
                case CLEANLINESS:
                    BigDecimal cleanlinessRentRefund = calculateCleanlinessRefund(severity, totalRentAmount);
                    refundAmount = totalDepositAmount.add(cleanlinessRentRefund);
                    penaltyPoints = calculateCleanlinessPenalty(severity);
                    break;
                case SAFETY_HEALTH:
                    BigDecimal safetyRentRefund = calculateSafetyHealthRefund(severity, totalRentAmount);
                    refundAmount = totalDepositAmount.add(safetyRentRefund);
                    penaltyPoints = calculateSafetyHealthPenalty(severity);
                    break;
                default:
                    break;
            }
        } else if (role == ComplainantRole.HOST) {
            switch (type) {
                case PROPERTY_DAMAGE:
                    refundAmount = calculatePropertyDamagePenalty(severity, totalDepositAmount);
                    penaltyPoints = calculatePropertyDamagePenaltyPoints(severity);
                    break;
                case EXTRA_CLEANING:
                    refundAmount = calculateExtraCleaningPenalty(severity, totalDepositAmount);
                    penaltyPoints = calculateExtraCleaningPenaltyPoints(severity);
                    break;
                case HOUSE_RULE_VIOLATION:
                    refundAmount = calculateHouseRuleViolationPenalty(severity, totalDepositAmount);
                    penaltyPoints = calculateHouseRuleViolationPenaltyPoints(severity);
                    break;
                case UNAUTHORIZED_GUESTS_OR_STAY:
                    refundAmount = calculateUnauthorizedGuestsPenalty(severity, totalDepositAmount);
                    penaltyPoints = calculateUnauthorizedGuestsPenaltyPoints(severity);
                    break;
                default:
                    break;
            }
        }

        return new PenaltyCalculationResult(refundAmount, penaltyPoints);
    }

    private BigDecimal calculateCleanlinessRefund(Severity severity, BigDecimal totalRent) {
        switch (severity) {
            case LOW: return totalRent.multiply(new BigDecimal("0.05")); // 5%
            case MEDIUM: return totalRent.multiply(new BigDecimal("0.125")); // 10-15% average
            case HIGH: return totalRent.multiply(new BigDecimal("0.325")); // 25-40% average
            case CRITICAL: return totalRent.multiply(new BigDecimal("0.50")); // 50%
            default: return BigDecimal.ZERO;
        }
    }

    private Integer calculateCleanlinessPenalty(Severity severity) {
        switch (severity) {
            case LOW: return 0;
            case MEDIUM: return 2;
            case HIGH: return 5;
            case CRITICAL: return 10;
            default: return 0;
        }
    }

    private BigDecimal calculateSafetyHealthRefund(Severity severity, BigDecimal totalRent) {
        switch (severity) {
            case LOW: return totalRent.multiply(new BigDecimal("0.10")); // 10% of rent
            case MEDIUM: return totalRent.multiply(new BigDecimal("0.30")); // 30% of rent
            case HIGH: return totalRent.multiply(new BigDecimal("0.70")); // 70% of rent
            case CRITICAL: return totalRent;
            default: return BigDecimal.ZERO;
        }
    }

    private Integer calculateSafetyHealthPenalty(Severity severity) {
        switch (severity) {
            case LOW: return 3;
            case MEDIUM: return 7;
            case HIGH: return 15;
            case CRITICAL: return 25;
            default: return 0;
        }
    }

    private BigDecimal calculatePropertyDamagePenalty(Severity severity, BigDecimal totalDeposit) {
        switch (severity) {
            case LOW: return totalDeposit.multiply(new BigDecimal("0.075")); // 5-10% average
            case MEDIUM: return totalDeposit.multiply(new BigDecimal("0.30")); // 20-40% average
            case HIGH: return totalDeposit.multiply(new BigDecimal("0.70")); // 60-80% average
            case CRITICAL: return totalDeposit; // 100%
            default: return BigDecimal.ZERO;
        }
    }

    private Integer calculatePropertyDamagePenaltyPoints(Severity severity) {
        switch (severity) {
            case LOW: return 2;
            case MEDIUM: return 5;
            case HIGH: return 10;
            case CRITICAL: return 15;
            default: return 0;
        }
    }

    private BigDecimal calculateExtraCleaningPenalty(Severity severity, BigDecimal totalDeposit) {
        switch (severity) {
            case LOW: return totalDeposit.multiply(new BigDecimal("0.075")); // 5-10% average
            case MEDIUM: return totalDeposit.multiply(new BigDecimal("0.20")); // 15-25% average
            case HIGH: return totalDeposit.multiply(new BigDecimal("0.40")); // 30-50% average
            case CRITICAL: return totalDeposit.multiply(new BigDecimal("0.70")); // 70%
            default: return BigDecimal.ZERO;
        }
    }

    private Integer calculateExtraCleaningPenaltyPoints(Severity severity) {
        switch (severity) {
            case LOW: return 1;
            case MEDIUM: return 3;
            case HIGH: return 5;
            case CRITICAL: return 8;
            default: return 0;
        }
    }

    private BigDecimal calculateHouseRuleViolationPenalty(Severity severity, BigDecimal totalDeposit) {
        switch (severity) {
            case LOW: return BigDecimal.ZERO; // Warning only
            case MEDIUM: return totalDeposit.multiply(new BigDecimal("0.15")); // 10-20% average
            case HIGH: return totalDeposit.multiply(new BigDecimal("0.50")); // 40-60% average
            case CRITICAL: return totalDeposit; // 100%
            default: return BigDecimal.ZERO;
        }
    }

    private Integer calculateHouseRuleViolationPenaltyPoints(Severity severity) {
        switch (severity) {
            case LOW: return 2;
            case MEDIUM: return 5;
            case HIGH: return 10;
            case CRITICAL: return 15;
            default: return 0;
        }
    }

    private BigDecimal calculateUnauthorizedGuestsPenalty(Severity severity, BigDecimal totalDeposit) {
        switch (severity) {
            case LOW: return totalDeposit.multiply(new BigDecimal("0.10")); // 10%
            case MEDIUM: return totalDeposit.multiply(new BigDecimal("0.325")); // 25-40% average
            case HIGH: return totalDeposit.multiply(new BigDecimal("0.70")); // 60-80% average
            case CRITICAL: return totalDeposit; // 100%
            default: return BigDecimal.ZERO;
        }
    }

    private Integer calculateUnauthorizedGuestsPenaltyPoints(Severity severity) {
        switch (severity) {
            case LOW: return 3;
            case MEDIUM: return 7;
            case HIGH: return 12;
            case CRITICAL: return 20;
            default: return 0;
        }
    }

    public static class PenaltyCalculationResult {
        private final BigDecimal refundAmount;
        private final Integer penaltyPoints;

        public PenaltyCalculationResult(BigDecimal refundAmount, Integer penaltyPoints) {
            this.refundAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);
            this.penaltyPoints = penaltyPoints;
        }

        public BigDecimal getRefundAmount() {
            return refundAmount;
        }

        public Integer getPenaltyPoints() {
            return penaltyPoints;
        }
    }
}

