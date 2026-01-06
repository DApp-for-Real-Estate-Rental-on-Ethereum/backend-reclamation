package ma.fstt.reclamationservice.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.fstt.reclamationservice.domain.entity.Reclamation;
import ma.fstt.reclamationservice.domain.entity.Reclamation.ComplainantRole;
import ma.fstt.reclamationservice.domain.entity.Reclamation.Severity;
import ma.fstt.reclamationservice.domain.entity.ReclamationAttachment;
import ma.fstt.reclamationservice.domain.entity.ReclamationStatus;
import ma.fstt.reclamationservice.domain.entity.ReclamationType;
import ma.fstt.reclamationservice.domain.repository.ReclamationAttachmentRepository;
import ma.fstt.reclamationservice.domain.repository.ReclamationRepository;
import ma.fstt.reclamationservice.core.blockchain.BookingPaymentContractService;
import ma.fstt.reclamationservice.core.service.UserScoreService;
import ma.fstt.reclamationservice.domain.entity.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReclamationService {
    @org.springframework.beans.factory.annotation.Value("${app.booking-service.url:http://booking-service.derent-cluster.local:8083}")
    private String bookingServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${app.property-service.url:http://property-service.derent-cluster.local:8081}")
    private String propertyServiceUrl;

    private final RestTemplate restTemplate;
    private final ReclamationRepository reclamationRepository;
    private final ReclamationAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final PenaltyCalculatorService penaltyCalculatorService;
    private final BookingPaymentContractService bookingPaymentContractService;
    private final UserScoreService userScoreService;

    @Transactional
    public Reclamation createReclamation(Long bookingId, Long userId, ComplainantRole role,
            ReclamationType type, String title, String description) {
        log.info("Creating reclamation: bookingId={}, userId={}, role={}, type={}", bookingId, userId, role, type);

        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        Long targetUserId = 0L;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> booking = restTemplate.getForObject(
                    bookingServiceUrl + "/api/bookings/" + bookingId,
                    Map.class);

            if (booking != null) {
                Long bookingUserId = Long.valueOf(booking.get("userId").toString());
                Object propertyIdObj = booking.get("propertyId");
                String propertyId = propertyIdObj != null ? propertyIdObj.toString() : null;

                log.info("📋 Booking details - bookingId: {}, bookingUserId: {}, propertyId: {}",
                        bookingId, bookingUserId, propertyId);

                if (propertyId != null) {
                    Long ownerId = null;

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propertyInfo = restTemplate.getForObject(
                                bookingServiceUrl + "/api/bookings/property/" + propertyId,
                                Map.class);
                        if (propertyInfo != null) {
                            log.info("✅ Got property info from booking-service");
                            log.info("🔍 Property info keys: {}", propertyInfo.keySet());
                            log.info("🔍 Property info content: {}", propertyInfo);

                            Object ownerIdObj = propertyInfo.get("ownerId");
                            if (ownerIdObj != null) {
                                try {
                                    if (ownerIdObj instanceof Number) {
                                        ownerId = ((Number) ownerIdObj).longValue();
                                    } else if (ownerIdObj instanceof String) {
                                        ownerId = Long.parseLong((String) ownerIdObj);
                                    } else {
                                        ownerId = Long.valueOf(ownerIdObj.toString());
                                    }
                                    log.info("✅ Extracted ownerId: {} from PropertyInfo", ownerId);
                                } catch (Exception e) {
                                    log.error("❌ Failed to convert ownerId: {} - {}", ownerIdObj, e.getMessage());
                                }
                            } else {
                                log.warn("⚠️ ownerId is null in PropertyInfo response");
                            }
                        }
                    } catch (Exception e1) {
                        log.warn("⚠️ Booking-service endpoint failed: {}", e1.getMessage());
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> propertyInfo = restTemplate.getForObject(
                                    propertyServiceUrl + "/api/v1/properties/" + propertyId + "/booking-info",
                                    Map.class);
                            if (propertyInfo != null) {
                                log.info("✅ Got property info from property-service (booking-info)");
                                log.info("🔍 Property info keys: {}", propertyInfo.keySet());

                                Object ownerIdObj = propertyInfo.get("ownerId");
                                if (ownerIdObj != null) {
                                    try {
                                        if (ownerIdObj instanceof Number) {
                                            ownerId = ((Number) ownerIdObj).longValue();
                                        } else if (ownerIdObj instanceof String) {
                                            ownerId = Long.parseLong((String) ownerIdObj);
                                        } else {
                                            ownerId = Long.valueOf(ownerIdObj.toString());
                                        }
                                        log.info("✅ Extracted ownerId: {} from PropertyBookingInfoDTO", ownerId);
                                    } catch (Exception e) {
                                        log.error("❌ Failed to convert ownerId: {} - {}", ownerIdObj, e.getMessage());
                                    }
                                }
                            }
                        } catch (Exception e2) {
                            log.error("❌ Property-service endpoint also failed: {}", e2.getMessage());
                        }
                    }

                    if (role == ComplainantRole.GUEST) {
                        if (ownerId != null) {
                            targetUserId = ownerId;
                            log.info("✅ Guest reclamation - targetUserId set to ownerId: {}", targetUserId);
                        } else {
                            log.error("❌ CRITICAL: Cannot set targetUserId for GUEST - ownerId is null. PropertyId: {}",
                                    propertyId);
                            log.error(
                                    "❌ This will cause the reclamation to not appear in 'Complaints Against Me' for the host");
                        }
                    } else if (role == ComplainantRole.HOST) {
                        targetUserId = bookingUserId;
                        log.info("✅ Host reclamation - targetUserId set to bookingUserId: {}", targetUserId);
                    }
                } else {
                    log.error("❌ PropertyId is null in booking");
                }
            } else {
                log.error("❌ Booking not found for bookingId: {}", bookingId);
            }
        } catch (Exception e) {
            log.error("❌ Could not fetch booking details: {}", e.getMessage(), e);
        }

        if (targetUserId == 0L) {
            log.error("⚠️ WARNING: targetUserId is 0! Reclamation may not be linked correctly.");
            log.error(
                    "⚠️ This will cause the reclamation to not appear in 'Complaints Against Me' for the target user.");
        } else {
            log.info("✅ Final targetUserId: {}", targetUserId);
        }

        Reclamation reclamation = Reclamation.builder()
                .bookingId(bookingId)
                .complainantId(userId)
                .complainantRole(role)
                .targetUserId(targetUserId)
                .type(type)
                .title(title)
                .description(description)
                .status(ReclamationStatus.OPEN)
                .severity(Severity.LOW)
                .refundAmount(BigDecimal.ZERO)
                .penaltyPoints(0)
                .build();

        reclamation = reclamationRepository.save(reclamation);

        final Long finalBookingId = bookingId;
        try {
            bookingPaymentContractService.setActiveReclamation(finalBookingId, true);
            log.info("✅ Set hasActiveReclamation=true for booking: {}", finalBookingId);
        } catch (Exception e) {
            log.warn("⚠️ Failed to set hasActiveReclamation flag: {}", e.getMessage());
        }

        return reclamation;
    }

    @Transactional
    public void saveAttachments(Long reclamationId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }

        reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Reclamation not found: " + reclamationId));

        long existingCount = attachmentRepository.countByReclamationId(reclamationId);
        if (existingCount + files.size() > 3) {
            throw new RuntimeException("Maximum 3 images allowed per reclamation");
        }

        List<String> savedPaths = fileStorageService.saveFiles(reclamationId, files);

        int order = (int) existingCount + 1;
        for (int i = 0; i < files.size() && i < savedPaths.size(); i++) {
            MultipartFile file = files.get(i);
            String filePath = savedPaths.get(i);

            ReclamationAttachment attachment = ReclamationAttachment.builder()
                    .reclamationId(reclamationId)
                    .filePath(filePath)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .displayOrder(order++)
                    .build();

            attachmentRepository.save(attachment);
        }
    }

    public List<Reclamation> getReclamationsByComplainantId(Long complainantId) {
        return reclamationRepository.findByComplainantId(complainantId);
    }

    public List<Reclamation> getReclamationsByTargetUserId(Long targetUserId) {
        log.info("🔍 Fetching reclamations against targetUserId: {}", targetUserId);
        List<Reclamation> reclamations = reclamationRepository.findByTargetUserId(targetUserId);
        log.info("📊 Found {} reclamations against targetUserId: {}", reclamations.size(), targetUserId);
        return reclamations;
    }

    public List<ReclamationAttachment> getAttachmentsByReclamationId(Long reclamationId) {
        return attachmentRepository.findByReclamationIdOrderByDisplayOrderAsc(reclamationId);
    }

    public Reclamation getReclamationByBookingIdAndComplainantId(Long bookingId, Long complainantId) {
        List<Reclamation> reclamations = reclamationRepository.findByBookingIdAndComplainantId(bookingId,
                complainantId);
        return reclamations.isEmpty() ? null : reclamations.get(0);
    }

    @Transactional
    public void deleteReclamation(Long reclamationId, Long userId) {
        log.info("Deleting reclamation: id={}, userId={}", reclamationId, userId);

        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Reclamation not found: " + reclamationId));

        if (!reclamation.getComplainantId().equals(userId)) {
            throw new RuntimeException("Only the complainant can delete this reclamation");
        }

        if (reclamation.getStatus() != ReclamationStatus.OPEN &&
                reclamation.getStatus() != ReclamationStatus.IN_REVIEW) {
            throw new RuntimeException("Cannot delete reclamation with status: " + reclamation.getStatus());
        }

        try {
            fileStorageService.deleteFiles(reclamationId);
            attachmentRepository.deleteByReclamationId(reclamationId);
        } catch (Exception e) {
            log.warn("Error deleting attachments for reclamation: {}", reclamationId, e);
        }

        reclamationRepository.delete(reclamation);

        try {
            bookingPaymentContractService.setActiveReclamation(reclamation.getBookingId(), false);
            log.info("✅ Cleared hasActiveReclamation flag for booking: {}", reclamation.getBookingId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to clear hasActiveReclamation flag: {}", e.getMessage());
        }

        log.info("Reclamation deleted successfully: id={}", reclamationId);
    }

    @Transactional
    @Deprecated
    public void resolveReclamation(Long reclamationId, BigDecimal depositToOwner, BigDecimal depositToTenant,
            Long dbTxId) {
        log.warn("⚠️ resolveReclamation is deprecated. Use resolveReclamationWithPenalty instead.");
        log.info("Resolving reclamation: id={}, depositToOwner={}, depositToTenant={}, dbTxId={}",
                reclamationId, depositToOwner, depositToTenant, dbTxId);

        log.warn(
                "⚠️ This method no longer calls payment-service. Use resolveReclamationWithPenalty for full functionality.");
    }

    @SuppressWarnings("unused")
    private static class CompleteWithDamageRequest {
        public Long dbTxId;
        public BigDecimal depositToOwner;

        public CompleteWithDamageRequest(Long dbTxId, BigDecimal depositToOwner) {
            this.dbTxId = dbTxId;
            this.depositToOwner = depositToOwner;
        }
    }

    @SuppressWarnings("unused")
    private static class CompleteNoDamageRequest {
        public Long dbTxId;

        public CompleteNoDamageRequest(Long dbTxId) {
            this.dbTxId = dbTxId;
        }
    }

    public List<Reclamation> getAllReclamations() {
        return reclamationRepository.findAll();
    }

    public List<Reclamation> getReclamationsByStatus(ReclamationStatus status) {
        return reclamationRepository.findAll().stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Reclamation getReclamationById(Long reclamationId) {
        return reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Reclamation not found: " + reclamationId));
    }

    @Transactional
    public Reclamation updateSeverity(Long reclamationId, Severity severity) {
        Reclamation reclamation = getReclamationById(reclamationId);
        reclamation.setSeverity(severity);
        return reclamationRepository.save(reclamation);
    }

    @Transactional
    public Reclamation reviewReclamation(Long reclamationId) {
        Reclamation reclamation = getReclamationById(reclamationId);
        reclamation.setStatus(ReclamationStatus.IN_REVIEW);
        return reclamationRepository.save(reclamation);
    }

    @Transactional
    public Reclamation resolveReclamationWithPenalty(
            Long reclamationId,
            String resolutionNotes,
            boolean approved) throws Exception {
        Reclamation reclamation = getReclamationById(reclamationId);

        if (reclamation.getTargetUserId() == null || reclamation.getTargetUserId() == 0) {
            log.warn("⚠️ targetUserId is null or 0 for reclamationId: {}, attempting to fix...", reclamationId);
            Long targetUserId = determineTargetUserIdFromBooking(reclamation);
            if (targetUserId != null && targetUserId > 0) {
                reclamation.setTargetUserId(targetUserId);
                reclamation = reclamationRepository.save(reclamation);
                log.info("✅ Fixed targetUserId: {} for reclamationId: {}", targetUserId, reclamationId);
            } else {
                log.error("❌ Could not determine targetUserId for reclamationId: {}", reclamationId);
            }
        }

        if (approved) {
            @SuppressWarnings("unchecked")
            Map<String, Object> booking = restTemplate.getForObject(
                    bookingServiceUrl + "/api/bookings/" + reclamation.getBookingId(),
                    Map.class);

            BigDecimal totalRent = BigDecimal.ZERO;
            BigDecimal totalDeposit = BigDecimal.ZERO;

            if (booking != null) {
                Object totalPriceObj = booking.get("totalPrice");
                if (totalPriceObj != null) {
                    totalRent = new BigDecimal(totalPriceObj.toString());
                }
                Object depositObj = booking.get("depositAmount");
                if (depositObj != null) {
                    totalDeposit = new BigDecimal(depositObj.toString());
                }
            }

            // CRITICAL: Log deposit amount to verify it's not zero
            log.info("🔍🔍🔍 DEPOSIT CHECK - ReclamationId: {}, BookingId: {}, TotalRent: {}, TotalDeposit: {}",
                    reclamationId, reclamation.getBookingId(), totalRent, totalDeposit);

            if (totalDeposit.compareTo(BigDecimal.ZERO) <= 0) {
                log.error(
                        "❌❌❌ CRITICAL ERROR: TotalDeposit is ZERO or NEGATIVE for reclamationId: {}, bookingId: {}! Deposit will NOT be refunded!",
                        reclamationId, reclamation.getBookingId());
            }

            PenaltyCalculatorService.PenaltyCalculationResult penaltyResult = penaltyCalculatorService
                    .calculatePenalty(reclamation, totalRent, totalDeposit);

            BigDecimal refundAmount = penaltyResult.getRefundAmount();
            Integer penaltyPoints = penaltyResult.getPenaltyPoints();

            log.info(
                    "📊 Penalty calculation result - refundAmount: {}, penaltyPoints: {}, reclamationType: {}, severity: {}",
                    refundAmount, penaltyPoints, reclamation.getType(), reclamation.getSeverity());

            reclamation.setRefundAmount(refundAmount);
            reclamation.setPenaltyPoints(penaltyPoints);
            reclamation.setStatus(ReclamationStatus.RESOLVED);
            reclamation.setResolutionNotes(resolutionNotes);

            final Reclamation savedReclamation = reclamationRepository.save(reclamation);
            reclamation = savedReclamation;

            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    String recipientAddress = getRecipientWalletAddress(reclamation);

                    String bookingStatus = booking != null ? booking.get("status").toString() : null;
                    boolean isBookingCompleted = "COMPLETED".equals(bookingStatus) ||
                            "TENANT_CHECKED_OUT".equals(bookingStatus);

                    if (recipientAddress != null) {
                        // Special handling for ACCESS_ISSUE and NOT_AS_DESCRIBED (full refund: Rent +
                        // Deposit)
                        ReclamationType type = reclamation.getType();
                        boolean isFullRefund = (type == ReclamationType.ACCESS_ISSUE ||
                                type == ReclamationType.NOT_AS_DESCRIBED) &&
                                reclamation.getComplainantRole() == ComplainantRole.GUEST;

                        if (isFullRefund) {
                            // Full refund: Rent (full, no platform fee) + Deposit (full)
                            // No platform fee for ACCESS_ISSUE and NOT_AS_DESCRIBED

                            log.info(
                                    "💰💰💰 FULL REFUND PROCESSING START - Type: {}, BookingId: {}, TotalRent: {}, TotalDeposit: {}",
                                    type, reclamation.getBookingId(), totalRent, totalDeposit);

                            BigInteger rentWei = totalRent
                                    .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                    .toBigInteger();
                            BigInteger depositWei = totalDeposit
                                    .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                    .toBigInteger();

                            log.info(
                                    "💰 Processing full refund (no platform fee): Rent={} ETH (full, wei={}), Deposit={} ETH (full, wei={})",
                                    totalRent, rentWei, totalDeposit, depositWei);

                            if (totalDeposit.compareTo(BigDecimal.ZERO) <= 0) {
                                log.error(
                                        "❌❌❌ CRITICAL: TotalDeposit is ZERO or NEGATIVE! Deposit will NOT be refunded!");
                            }
                            if (depositWei.compareTo(BigInteger.ZERO) <= 0) {
                                log.error(
                                        "❌❌❌ CRITICAL: DepositWei is ZERO or NEGATIVE! Deposit will NOT be refunded!");
                            }

                            // Calculate total refund to guest (Rent full + Deposit full)
                            BigInteger totalRefundToGuest = rentWei.add(depositWei);

                            // For both completed and non-completed bookings, use processPartialRefund
                            // to send Rent and Deposit separately to ensure correct refund

                            // Refund Rent (full, no platform fee)
                            if (rentWei.compareTo(BigInteger.ZERO) > 0) {
                                log.info("🔄 Sending Rent refund: {} ETH (wei={}) to guest address: {}",
                                        totalRent, rentWei, recipientAddress);
                                try {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress,
                                            rentWei,
                                            true // refundFromRent = true
                                    );
                                    log.info("✅✅✅ Rent refund SUCCESSFULLY sent: {} ETH to guest", totalRent);
                                } catch (Exception e) {
                                    log.error("❌❌❌ FAILED to send Rent refund: {}", e.getMessage(), e);
                                    throw e;
                                }
                            } else {
                                log.warn("⚠️ RentWei is ZERO, skipping Rent refund");
                            }

                            // Refund Deposit (full) - THIS IS THE CRITICAL PART
                            if (depositWei.compareTo(BigInteger.ZERO) > 0) {
                                log.info("🔄🔄🔄 Sending Deposit refund: {} ETH (wei={}) to guest address: {}",
                                        totalDeposit, depositWei, recipientAddress);
                                try {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress,
                                            depositWei,
                                            false // refundFromRent = false (refund from deposit) - CRITICAL!
                                    );
                                    log.info("✅✅✅ Deposit refund SUCCESSFULLY sent: {} ETH to guest", totalDeposit);
                                } catch (Exception e) {
                                    log.error("❌❌❌ FAILED to send Deposit refund: {}", e.getMessage(), e);
                                    throw e;
                                }
                            } else {
                                log.error("❌❌❌ DepositWei is ZERO or NEGATIVE! Deposit refund will NOT be sent!");
                            }

                            log.info(
                                    "✅✅✅ Full refund processed (no platform fee): Guest={} ETH (Rent: {} + Deposit: {})",
                                    new BigDecimal(totalRefundToGuest)
                                            .divide(BigDecimal.valueOf(1_000_000_000_000_000_000L)),
                                    totalRent, totalDeposit);
                        } else {
                            // Handle CLEANLINESS and SAFETY_HEALTH: Deposit + Rent (after platform fee)
                            // Handle Host reclamations: Rent to host + penalty from deposit

                            ReclamationType reclamationType = reclamation.getType();
                            boolean isGuestRefundWithDeposit = (reclamationType == ReclamationType.CLEANLINESS ||
                                    reclamationType == ReclamationType.SAFETY_HEALTH) &&
                                    reclamation.getComplainantRole() == ComplainantRole.GUEST;

                            if (isGuestRefundWithDeposit) {
                                // For CLEANLINESS and SAFETY_HEALTH:
                                // refundAmount = Deposit (full) + Rent percentage (full, not after fee)
                                // Guest gets: Deposit + Rent percentage (full)
                                // Platform gets: 10% of Rent percentage
                                // Host gets: 90% of Rent percentage + remaining rent

                                BigDecimal depositPortion = totalDeposit;
                                BigDecimal rentPercentage = refundAmount.subtract(depositPortion); // Rent percentage
                                                                                                   // (full)

                                // Calculate platform fee (10% of rent percentage)
                                BigDecimal platformFeePercent = new BigDecimal("0.10");
                                BigDecimal platformFee = rentPercentage.multiply(platformFeePercent);
                                BigDecimal rentPercentageToHost = rentPercentage.subtract(platformFee); // 90% of
                                                                                                        // percentage
                                BigDecimal remainingRent = totalRent.subtract(rentPercentage); // Remaining rent after
                                                                                               // percentage
                                BigDecimal totalToHost = rentPercentageToHost.add(remainingRent); // Host gets: 90% of
                                                                                                  // percentage +
                                                                                                  // remaining rent

                                BigInteger depositWei = depositPortion
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();
                                BigInteger rentPercentageWei = rentPercentage
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();
                                BigInteger platformFeeWei = platformFee
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();
                                BigInteger totalToHostWei = totalToHost
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();

                                log.info(
                                        "💰 Processing CLEANLINESS/SAFETY_HEALTH: Guest gets Deposit={} + Rent percentage={}, Platform gets {} (10% of percentage), Host gets {} (90% of percentage + remaining rent={})",
                                        depositPortion, rentPercentage, platformFee, totalToHost, remainingRent);

                                BigInteger totalRefundToGuest = depositWei.add(rentPercentageWei);

                                // Get host wallet address
                                String hostAddress = getRecipientWalletAddressForHost(reclamation);

                                // For both completed and non-completed bookings, use processPartialRefund
                                // to send multiple amounts to different recipients

                                // Send Deposit to guest - CRITICAL: This must be sent!
                                if (depositWei.compareTo(BigInteger.ZERO) > 0) {
                                    log.info(
                                            "🔄🔄🔄 CLEANLINESS/SAFETY_HEALTH: Sending Deposit refund: {} ETH (wei={}) to guest address: {}",
                                            depositPortion, depositWei, recipientAddress);
                                    try {
                                        bookingPaymentContractService.processPartialRefund(
                                                reclamation.getBookingId(),
                                                recipientAddress, // Guest address
                                                depositWei,
                                                false // refundFromRent = false (refund from deposit) - CRITICAL!
                                        );
                                        log.info(
                                                "✅✅✅ CLEANLINESS/SAFETY_HEALTH: Deposit refund SUCCESSFULLY sent: {} ETH to guest",
                                                depositPortion);
                                    } catch (Exception e) {
                                        log.error("❌❌❌ CLEANLINESS/SAFETY_HEALTH: FAILED to send Deposit refund: {}",
                                                e.getMessage(), e);
                                        throw e;
                                    }
                                } else {
                                    log.error(
                                            "❌❌❌ CLEANLINESS/SAFETY_HEALTH: DepositWei is ZERO! Deposit refund will NOT be sent!");
                                }

                                // Send Rent percentage (full) to guest
                                if (rentPercentageWei.compareTo(BigInteger.ZERO) > 0) {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress, // Guest address
                                            rentPercentageWei,
                                            true // refundFromRent = true
                                    );
                                }

                                // Send Platform Fee (10% of Rent percentage)
                                if (platformFeeWei.compareTo(BigInteger.ZERO) > 0) {
                                    String platformWallet = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            platformWallet,
                                            platformFeeWei,
                                            true // refundFromRent = true
                                    );
                                }

                                // Send to Host (90% of percentage + remaining rent)
                                if (totalToHostWei.compareTo(BigInteger.ZERO) > 0 && hostAddress != null) {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            hostAddress,
                                            totalToHostWei,
                                            true // refundFromRent = true
                                    );
                                }

                                log.info(
                                        "✅ Refund processed: Guest={} ETH (Deposit: {} + Rent percentage: {}), Platform={} ETH, Host={} ETH",
                                        new BigDecimal(totalRefundToGuest)
                                                .divide(BigDecimal.valueOf(1_000_000_000_000_000_000L)),
                                        depositPortion, rentPercentage, platformFee, totalToHost);
                            } else if (reclamation.getComplainantRole() == ComplainantRole.HOST) {
                                // For Host reclamations:
                                // Rent: 10% to platform, 90% to host
                                // Deposit: penalty percentage to host, remaining to guest

                                // Calculate Rent distribution (10% platform, 90% host)
                                BigDecimal platformFeePercent = new BigDecimal("0.10");
                                BigDecimal platformFeeFromRent = totalRent.multiply(platformFeePercent);
                                BigDecimal rentToHost = totalRent.subtract(platformFeeFromRent);

                                // Deposit distribution: penalty to host, remaining to guest
                                BigDecimal penaltyFromDeposit = refundAmount; // This is the penalty amount from deposit
                                BigDecimal depositToGuest = totalDeposit.subtract(penaltyFromDeposit);

                                BigInteger rentToHostWei = rentToHost
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();
                                BigInteger platformFeeFromRentWei = platformFeeFromRent
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();
                                BigInteger penaltyFromDepositWei = penaltyFromDeposit
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();
                                BigInteger depositToGuestWei = depositToGuest
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();

                                // Get guest wallet address
                                String guestAddress = getRecipientWalletAddressForGuest(reclamation);

                                log.info(
                                        "💰 Processing host reclamation: Rent (90%={} to host, 10%={} to platform), Deposit (penalty={} to host, remaining={} to guest)",
                                        rentToHost, platformFeeFromRent, penaltyFromDeposit, depositToGuest);

                                // For both completed and non-completed bookings, use processPartialRefund
                                // to send multiple amounts to different recipients

                                // Send Rent (90%) to host
                                if (rentToHostWei.compareTo(BigInteger.ZERO) > 0) {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress, // Host address
                                            rentToHostWei,
                                            true // refundFromRent = true
                                    );
                                }

                                // Send Platform Fee (10% of rent)
                                if (platformFeeFromRentWei.compareTo(BigInteger.ZERO) > 0) {
                                    String platformWallet = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            platformWallet,
                                            platformFeeFromRentWei,
                                            true // refundFromRent = true
                                    );
                                }

                                // Send Penalty from deposit to host
                                if (penaltyFromDepositWei.compareTo(BigInteger.ZERO) > 0) {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress, // Host address
                                            penaltyFromDepositWei,
                                            false // refundFromRent = false (penalty from deposit)
                                    );
                                }

                                // Send Remaining deposit to guest
                                if (depositToGuestWei.compareTo(BigInteger.ZERO) > 0 && guestAddress != null) {
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            guestAddress,
                                            depositToGuestWei,
                                            false // refundFromRent = false (refund from deposit)
                                    );
                                }

                                BigInteger totalToHost = rentToHostWei.add(penaltyFromDepositWei);
                                log.info(
                                        "✅ Host reclamation processed: Host={} ETH (Rent 90%: {} + Penalty: {}), Platform={} ETH, Guest={} ETH",
                                        new BigDecimal(totalToHost)
                                                .divide(BigDecimal.valueOf(1_000_000_000_000_000_000L)),
                                        rentToHost, penaltyFromDeposit, platformFeeFromRent, depositToGuest);

                                // Note: For completed bookings, we use processPartialRefund instead of
                                // processReclamationRefund
                                // because we need to send to multiple recipients (host, platform, guest)
                            } else {
                                // Normal guest refund (should not happen with new system, but keep for safety)
                                BigInteger refundAmountWei = refundAmount
                                        .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                        .toBigInteger();

                                boolean refundFromRent = reclamation.getComplainantRole() == ComplainantRole.GUEST;

                                // Calculate and send platform fee for guest refunds (10% of original refund
                                // before fee deduction)
                                BigInteger platformFeeWei = BigInteger.ZERO;
                                if (refundFromRent && reclamation.getComplainantRole() == ComplainantRole.GUEST) {
                                    // refundAmount = originalRefund * 0.90, so originalRefund = refundAmount / 0.90
                                    BigDecimal originalRefund = refundAmount.divide(new BigDecimal("0.90"), 2,
                                            RoundingMode.HALF_UP);
                                    BigDecimal platformFee = originalRefund.subtract(refundAmount);
                                    platformFeeWei = platformFee
                                            .multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L))
                                            .toBigInteger();

                                    log.info(
                                            "💰 Calculating platform fee: original refund={} ETH, refund after fee={} ETH, platform fee={} ETH",
                                            originalRefund, refundAmount, platformFee);
                                }

                                // Send refund and platform fee in one call for completed bookings
                                if (isBookingCompleted) {
                                    log.info(
                                            "💰 Processing refund for completed booking: Guest={} ETH, Platform Fee={} ETH",
                                            refundAmount, platformFeeWei);
                                    bookingPaymentContractService.processReclamationRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress,
                                            refundAmountWei, // Refund to guest
                                            platformFeeWei, // Platform fee to PLATFORM_WALLET
                                            refundFromRent);
                                    if (platformFeeWei.compareTo(BigInteger.ZERO) > 0) {
                                        log.info("✅ Platform fee sent to platform wallet: {} ETH",
                                                new BigDecimal(platformFeeWei)
                                                        .divide(BigDecimal.valueOf(1_000_000_000_000_000_000L)));
                                    }
                                } else {
                                    log.info("📋 Booking not completed yet. Using processPartialRefund");

                                    // Send platform fee first (if any)
                                    if (platformFeeWei.compareTo(BigInteger.ZERO) > 0) {
                                        String platformWallet = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
                                        bookingPaymentContractService.processPartialRefund(
                                                reclamation.getBookingId(),
                                                platformWallet,
                                                platformFeeWei,
                                                true // refundFromRent = true (platform fee comes from rent)
                                        );
                                        log.info("💰 Platform fee sent to platform wallet: {} ETH",
                                                new BigDecimal(platformFeeWei)
                                                        .divide(BigDecimal.valueOf(1_000_000_000_000_000_000L)));
                                    }

                                    // Then send refund to guest
                                    bookingPaymentContractService.processPartialRefund(
                                            reclamation.getBookingId(),
                                            recipientAddress,
                                            refundAmountWei,
                                            refundFromRent);
                                }
                            }
                        }
                    } else {
                        log.warn("⚠️ Cannot process refund: recipient wallet address is null");
                    }

                    log.info("✅ Blockchain refund processed for reclamation: {}", reclamationId);
                } catch (Exception e) {
                    log.error("❌ Failed to process blockchain refund: {}", e.getMessage(), e);
                }
            }

            if (penaltyPoints != null && penaltyPoints > 0) {
                log.info(
                        "🔍 Checking penalty points application - penaltyPoints: {}, targetUserId: {}, reclamationId: {}",
                        penaltyPoints, reclamation.getTargetUserId(), reclamationId);

                if (reclamation.getTargetUserId() != null && reclamation.getTargetUserId() > 0) {
                    log.info("🔗 Deducting penalty points directly: userId={}, penaltyPoints={}",
                            reclamation.getTargetUserId(), penaltyPoints);

                    try {
                        userScoreService.deductPenaltyPoints(reclamation.getTargetUserId(), penaltyPoints);
                        log.info("✅ Penalty points deducted successfully for user: {}", reclamation.getTargetUserId());
                        checkAndSuspendProperty(reclamation.getBookingId(), reclamation.getTargetUserId(),
                                penaltyPoints);
                    } catch (Exception e) {
                        log.error("❌ Failed to deduct penalty points: {}", e.getMessage(), e);
                    }
                } else {
                    log.error(
                            "❌ CRITICAL: Cannot update penalty points - targetUserId is null or 0! reclamationId: {}, targetUserId: {}",
                            reclamationId, reclamation.getTargetUserId());
                    log.error("❌ This means the penalty will NOT be applied to the user's score!");
                }
            } else {
                log.warn("⚠️ No penalty points to apply: penaltyPoints={} (reclamationId: {})", penaltyPoints,
                        reclamationId);
            }

        } else {
            reclamation.setStatus(ReclamationStatus.REJECTED);
            reclamation.setResolutionNotes(resolutionNotes);
            final Reclamation savedReclamation = reclamationRepository.save(reclamation);
            reclamation = savedReclamation;
        }

        final Long finalBookingIdForClear = reclamation.getBookingId();
        try {
            bookingPaymentContractService.setActiveReclamation(finalBookingIdForClear, false);
            log.info("✅ Set hasActiveReclamation=false for booking: {}", finalBookingIdForClear);
        } catch (Exception e) {
            log.warn("⚠️ Failed to clear hasActiveReclamation flag: {}", e.getMessage());
        }

        updateBookingStatusToCompleted(reclamation.getBookingId());

        return reclamation;
    }

    @Transactional
    public Reclamation rejectReclamation(Long reclamationId, String rejectionNotes) {
        Reclamation reclamation = getReclamationById(reclamationId);
        reclamation.setStatus(ReclamationStatus.REJECTED);
        reclamation.setResolutionNotes(rejectionNotes);
        reclamation = reclamationRepository.save(reclamation);

        updateBookingStatusToCompleted(reclamation.getBookingId());

        return reclamation;
    }

    private String getRecipientWalletAddress(Reclamation reclamation) {
        try {
            Long userId = reclamation.getComplainantRole() == ComplainantRole.GUEST
                    ? reclamation.getComplainantId()
                    : reclamation.getTargetUserId();

            UserAccount user = userScoreService.getUserAccountRepository().findById(userId).orElse(null);

            if (user != null && user.getWalletAddress() != null) {
                return user.getWalletAddress();
            }
        } catch (Exception e) {
            log.warn("Could not fetch wallet address: {}", e.getMessage());
        }
        return null;
    }

    private String getRecipientWalletAddressForHost(Reclamation reclamation) {
        try {
            // For guest reclamations, host is the target user
            // For host reclamations, host is the complainant
            Long hostUserId = reclamation.getComplainantRole() == ComplainantRole.GUEST
                    ? reclamation.getTargetUserId()
                    : reclamation.getComplainantId();

            if (hostUserId == null || hostUserId == 0) {
                log.warn("Host user ID is null or 0 for reclamation: {}", reclamation.getId());
                return null;
            }

            UserAccount host = userScoreService.getUserAccountRepository().findById(hostUserId).orElse(null);

            if (host != null && host.getWalletAddress() != null) {
                return host.getWalletAddress();
            } else {
                log.warn("Host wallet address not found for user ID: {}", hostUserId);
            }
        } catch (Exception e) {
            log.warn("Could not fetch host wallet address: {}", e.getMessage());
        }
        return null;
    }

    private String getRecipientWalletAddressForGuest(Reclamation reclamation) {
        try {
            // For host reclamations, guest is the target user
            // For guest reclamations, guest is the complainant
            Long guestUserId = reclamation.getComplainantRole() == ComplainantRole.HOST
                    ? reclamation.getTargetUserId()
                    : reclamation.getComplainantId();

            if (guestUserId == null || guestUserId == 0) {
                log.warn("Guest user ID is null or 0 for reclamation: {}", reclamation.getId());
                return null;
            }

            UserAccount guest = userScoreService.getUserAccountRepository().findById(guestUserId).orElse(null);

            if (guest != null && guest.getWalletAddress() != null) {
                return guest.getWalletAddress();
            } else {
                log.warn("Guest wallet address not found for user ID: {}", guestUserId);
            }
        } catch (Exception e) {
            log.warn("Could not fetch guest wallet address: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static class ReclamationRefundRequest {
        public Long bookingId;
        public String recipientAddress;
        public String refundAmountWei;
        public String penaltyAmountWei;
        public boolean refundFromRent;

        public ReclamationRefundRequest(Long bookingId, String recipientAddress,
                String refundAmountWei, String penaltyAmountWei,
                boolean refundFromRent) {
            this.bookingId = bookingId;
            this.recipientAddress = recipientAddress;
            this.refundAmountWei = refundAmountWei;
            this.penaltyAmountWei = penaltyAmountWei;
            this.refundFromRent = refundFromRent;
        }
    }

    private void updateBookingStatusToCompleted(Long bookingId) {
        try {
            log.info("🔄 Updating booking status to COMPLETED: bookingId={}", bookingId);

            Map<String, String> statusUpdate = new HashMap<>();
            statusUpdate.put("status", "COMPLETED");

            String url = bookingServiceUrl + "/api/bookings/" + bookingId + "/status";
            restTemplate.put(url, statusUpdate);

            log.info("✅ Booking status updated to COMPLETED: bookingId={}", bookingId);
        } catch (Exception e) {
            log.error("❌ Failed to update booking status to COMPLETED: bookingId={}, error={}",
                    bookingId, e.getMessage(), e);
        }
    }

    private void checkAndSuspendProperty(Long bookingId, Long hostUserId, Integer penaltyPoints) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> booking = restTemplate.getForObject(
                    bookingServiceUrl + "/api/bookings/" + bookingId,
                    Map.class);

            if (booking != null && booking.get("propertyId") != null) {
                String propertyId = booking.get("propertyId").toString();

                if (penaltyPoints >= 15) {
                    try {
                        log.warn("⚠️ Property {} should be suspended due to host penalty points: {}", propertyId,
                                penaltyPoints);
                        log.warn("⚠️ Admin should manually suspend property {} via admin dashboard", propertyId);
                    } catch (Exception e) {
                        log.error("Failed to suspend property: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not check property suspension: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private static class PenaltyPointsRequest {
        public Integer penaltyPoints;

        public PenaltyPointsRequest(Integer penaltyPoints) {
            this.penaltyPoints = penaltyPoints;
        }
    }

    @SuppressWarnings("unused")
    private static class PropertySuspensionRequest {
        public String reason;

        public PropertySuspensionRequest(String reason) {
            this.reason = reason;
        }
    }

    private Long determineTargetUserIdFromBooking(Reclamation reclamation) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> booking = restTemplate.getForObject(
                    bookingServiceUrl + "/api/bookings/" + reclamation.getBookingId(),
                    Map.class);

            if (booking != null) {
                Long bookingUserId = Long.valueOf(booking.get("userId").toString());
                Object propertyIdObj = booking.get("propertyId");
                String propertyId = propertyIdObj != null ? propertyIdObj.toString() : null;

                if (propertyId != null) {
                    Long ownerId = null;

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propertyInfo = restTemplate.getForObject(
                                bookingServiceUrl + "/api/bookings/property/" + propertyId,
                                Map.class);
                        if (propertyInfo != null) {
                            Object ownerIdObj = propertyInfo.get("ownerId");
                            if (ownerIdObj != null) {
                                if (ownerIdObj instanceof Number) {
                                    ownerId = ((Number) ownerIdObj).longValue();
                                } else if (ownerIdObj instanceof String) {
                                    ownerId = Long.parseLong((String) ownerIdObj);
                                } else {
                                    ownerId = Long.valueOf(ownerIdObj.toString());
                                }
                            }
                        }
                    } catch (Exception e1) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> propertyInfo = restTemplate.getForObject(
                                    propertyServiceUrl + "/api/v1/properties/" + propertyId + "/booking-info",
                                    Map.class);
                            if (propertyInfo != null) {
                                Object ownerIdObj = propertyInfo.get("ownerId");
                                if (ownerIdObj != null) {
                                    if (ownerIdObj instanceof Number) {
                                        ownerId = ((Number) ownerIdObj).longValue();
                                    } else if (ownerIdObj instanceof String) {
                                        ownerId = Long.parseLong((String) ownerIdObj);
                                    } else {
                                        ownerId = Long.valueOf(ownerIdObj.toString());
                                    }
                                }
                            }
                        } catch (Exception e2) {
                            log.warn("Could not fetch property info: {}", e2.getMessage());
                        }
                    }

                    if (reclamation.getComplainantRole() == ComplainantRole.GUEST) {
                        return ownerId;
                    } else if (reclamation.getComplainantRole() == ComplainantRole.HOST) {
                        return bookingUserId;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error determining targetUserId: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get phone number for a user by ID
     */
    public String getUserPhoneNumber(Long userId) {
        try {
            UserAccount user = userScoreService.getUserAccountRepository().findById(userId).orElse(null);
            if (user != null && user.getPhoneNumber() != null) {
                return String.valueOf(user.getPhoneNumber());
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching phone number for user: {}", userId, e);
            return null;
        }
    }

    /**
     * Update reclamation (title, description, images)
     * Only allowed if status is OPEN and user is the complainant
     */
    @Transactional
    public Reclamation updateReclamation(Long reclamationId, Long userId, String title, String description,
            List<MultipartFile> newImages) {
        log.info("Updating reclamation: id={}, userId={}", reclamationId, userId);

        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Reclamation not found"));

        // Check if user is the complainant
        if (!reclamation.getComplainantId().equals(userId)) {
            throw new RuntimeException("Only the complainant can update this reclamation");
        }

        // Check if status allows updates (only OPEN status)
        if (reclamation.getStatus() != ReclamationStatus.OPEN) {
            throw new RuntimeException("Cannot update reclamation. Status must be OPEN");
        }

        // Update title and description
        if (title != null && !title.trim().isEmpty()) {
            reclamation.setTitle(title.trim());
        }
        if (description != null && !description.trim().isEmpty()) {
            reclamation.setDescription(description.trim());
        }

        // Update images if provided
        // If newImages is provided (even if empty), replace all old images
        // If newImages is null, keep existing images unchanged
        if (newImages != null) {
            if (newImages.size() > 3) {
                throw new RuntimeException("Maximum 3 images allowed");
            }

            try {
                // Delete old attachments only if new images are provided
                if (!newImages.isEmpty()) {
                    List<ReclamationAttachment> oldAttachments = attachmentRepository
                            .findByReclamationIdOrderByDisplayOrderAsc(reclamationId);
                    for (ReclamationAttachment attachment : oldAttachments) {
                        try {
                            fileStorageService.deleteFile(reclamationId, attachment.getFilePath());
                        } catch (Exception e) {
                            log.warn("Failed to delete old file: {}", attachment.getFilePath(), e);
                        }
                    }
                    attachmentRepository.deleteAll(oldAttachments);

                    // Save new attachments
                    List<String> savedPaths = fileStorageService.saveFiles(reclamationId, newImages);
                    int order = 1;
                    for (String filePath : savedPaths) {
                        ReclamationAttachment attachment = ReclamationAttachment.builder()
                                .reclamationId(reclamationId)
                                .filePath(filePath)
                                .fileName(filePath.substring(filePath.lastIndexOf('/') + 1))
                                .displayOrder(order++)
                                .build();
                        attachmentRepository.save(attachment);
                    }

                    log.info("Updated {} images for reclamation: {}", savedPaths.size(), reclamationId);
                } else {
                    // If empty list is provided, delete all images
                    List<ReclamationAttachment> oldAttachments = attachmentRepository
                            .findByReclamationIdOrderByDisplayOrderAsc(reclamationId);
                    for (ReclamationAttachment attachment : oldAttachments) {
                        try {
                            fileStorageService.deleteFile(reclamationId, attachment.getFilePath());
                        } catch (Exception e) {
                            log.warn("Failed to delete old file: {}", attachment.getFilePath(), e);
                        }
                    }
                    attachmentRepository.deleteAll(oldAttachments);
                    log.info("Removed all images for reclamation: {}", reclamationId);
                }
            } catch (IOException e) {
                log.error("Error updating images for reclamation: {}", reclamationId, e);
                throw new RuntimeException("Failed to update images: " + e.getMessage());
            }
        }
        // If newImages is null, keep existing images unchanged

        reclamation = reclamationRepository.save(reclamation);
        log.info("✅ Reclamation updated: id={}", reclamationId);
        return reclamation;
    }

    public ma.fstt.reclamationservice.api.dto.ReclamationStatsDTO getReclamationStats(Long userId) {
        Long totalFiled = reclamationRepository.countByComplainantId(userId);
        Long totalReceived = reclamationRepository.countByTargetUserId(userId);

        List<ma.fstt.reclamationservice.domain.entity.ReclamationStatus> pendingStatuses = List.of(
                ma.fstt.reclamationservice.domain.entity.ReclamationStatus.OPEN,
                ma.fstt.reclamationservice.domain.entity.ReclamationStatus.IN_REVIEW);

        // This relies on the new method I added to the repository
        Long pendingFiled = reclamationRepository.countByComplainantIdAndStatusIn(userId, pendingStatuses);

        Long resolvedFiled = reclamationRepository.countByComplainantIdAndStatus(userId,
                ma.fstt.reclamationservice.domain.entity.ReclamationStatus.RESOLVED);

        return ma.fstt.reclamationservice.api.dto.ReclamationStatsDTO.builder()
                .id(userId)
                .totalFiled(totalFiled != null ? totalFiled : 0L)
                .totalReceived(totalReceived != null ? totalReceived : 0L)
                .pendingFiled(pendingFiled != null ? pendingFiled : 0L)
                .resolvedFiled(resolvedFiled != null ? resolvedFiled : 0L)
                .build();
    }
}
