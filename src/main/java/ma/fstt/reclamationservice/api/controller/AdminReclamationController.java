package ma.fstt.reclamationservice.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.fstt.reclamationservice.core.service.ReclamationService;
import ma.fstt.reclamationservice.domain.entity.Reclamation;
import ma.fstt.reclamationservice.domain.entity.Reclamation.Severity;
import ma.fstt.reclamationservice.domain.entity.ReclamationStatus;
import ma.fstt.reclamationservice.domain.entity.ReclamationAttachment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Controller for managing reclamations
 * Only admins should have access to these endpoints
 */
@RestController
@RequestMapping("/api/admin/reclamations")
// CORS is handled by API Gateway - no @CrossOrigin here
@RequiredArgsConstructor
@Slf4j
public class AdminReclamationController {

    private final ReclamationService reclamationService;

    /**
     * Get all reclamations (for admin dashboard)
     */
    @GetMapping
    public ResponseEntity<List<Reclamation>> getAllReclamations() {
        try {
            List<Reclamation> reclamations = reclamationService.getAllReclamations();
            return ResponseEntity.ok(reclamations);
        } catch (Exception e) {
            log.error("Error fetching all reclamations", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get reclamations by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Reclamation>> getReclamationsByStatus(@PathVariable String status) {
        try {
            ReclamationStatus reclamationStatus = ReclamationStatus.valueOf(status.toUpperCase());
            List<Reclamation> reclamations = reclamationService.getReclamationsByStatus(reclamationStatus);
            return ResponseEntity.ok(reclamations);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", status);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching reclamations by status: {}", status, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get reclamation by ID
     */
    @GetMapping("/{reclamationId}")
    public ResponseEntity<Reclamation> getReclamationById(@PathVariable Long reclamationId) {
        try {
            Reclamation reclamation = reclamationService.getReclamationById(reclamationId);
            return ResponseEntity.ok(reclamation);
        } catch (RuntimeException e) {
            log.error("Reclamation not found: {}", reclamationId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching reclamation: {}", reclamationId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Update reclamation severity
     */
    @PutMapping("/{reclamationId}/severity")
    public ResponseEntity<Reclamation> updateSeverity(
            @PathVariable Long reclamationId,
            @RequestParam String severity) {
        try {
            Severity severityEnum = Severity.valueOf(severity.toUpperCase());
            Reclamation reclamation = reclamationService.updateSeverity(reclamationId, severityEnum);
            return ResponseEntity.ok(reclamation);
        } catch (IllegalArgumentException e) {
            log.error("Invalid severity: {}", severity);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating severity for reclamation: {}", reclamationId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Review reclamation (move to IN_REVIEW status)
     */
    @PutMapping("/{reclamationId}/review")
    public ResponseEntity<Reclamation> reviewReclamation(@PathVariable Long reclamationId) {
        try {
            Reclamation reclamation = reclamationService.reviewReclamation(reclamationId);
            return ResponseEntity.ok(reclamation);
        } catch (Exception e) {
            log.error("Error reviewing reclamation: {}", reclamationId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Resolve reclamation with penalty calculation
     * This is the main endpoint for processing reclamations
     */
    @PostMapping("/{reclamationId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveReclamation(
            @PathVariable Long reclamationId,
            @RequestBody ResolveReclamationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Reclamation reclamation = reclamationService.resolveReclamationWithPenalty(
                    reclamationId,
                    request.getResolutionNotes(),
                    request.isApproved());

            response.put("status", "success");
            response.put("message", "Reclamation resolved successfully");
            response.put("reclamation", reclamation);
            response.put("refundAmount", reclamation.getRefundAmount());
            response.put("penaltyPoints", reclamation.getPenaltyPoints());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resolving reclamation: {}", reclamationId, e);
            response.put("status", "error");
            response.put("message", "Failed to resolve reclamation: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Reject reclamation
     */
    @PostMapping("/{reclamationId}/reject")
    public ResponseEntity<Map<String, Object>> rejectReclamation(
            @PathVariable Long reclamationId,
            @RequestBody RejectReclamationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Reclamation reclamation = reclamationService.rejectReclamation(
                    reclamationId,
                    request.getRejectionNotes());

            response.put("status", "success");
            response.put("message", "Reclamation rejected");
            response.put("reclamation", reclamation);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error rejecting reclamation: {}", reclamationId, e);
            response.put("status", "error");
            response.put("message", "Failed to reject reclamation: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get reclamation attachments (images)
     */
    @GetMapping("/{reclamationId}/attachments")
    public ResponseEntity<List<ReclamationAttachment>> getAttachments(@PathVariable Long reclamationId) {
        try {
            List<ReclamationAttachment> attachments = reclamationService.getAttachmentsByReclamationId(reclamationId);
            log.info("📸 Found {} attachments for reclamation: {}", attachments.size(), reclamationId);
            return ResponseEntity.ok(attachments);
        } catch (Exception e) {
            log.error("Error fetching attachments for reclamation: {}", reclamationId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Serve reclamation image file
     */
    @GetMapping("/files/{reclamationId}/{filename:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable Long reclamationId,
            @PathVariable String filename) {
        try {
            log.info("🖼️ Serving file: reclamationId={}, filename={}", reclamationId, filename);

            // Decode URL-encoded filename
            String decodedFilename = java.net.URLDecoder.decode(filename, "UTF-8");
            log.info("📝 Decoded filename: {}", decodedFilename);

            // Use the configured storage path
            Path filePath = Paths.get("./uploads/reclamations", String.valueOf(reclamationId), decodedFilename)
                    .normalize();
            log.info("📁 Looking for file at: {}", filePath.toAbsolutePath());

            // Check if file exists
            if (!java.nio.file.Files.exists(filePath)) {
                log.warn("❌ File does not exist: {}", filePath.toAbsolutePath());
                // Try with original filename (in case decoding wasn't needed)
                if (!filename.equals(decodedFilename)) {
                    Path altPath = Paths.get("./uploads/reclamations", String.valueOf(reclamationId), filename)
                            .normalize();
                    log.info("🔄 Trying alternative path: {}", altPath.toAbsolutePath());
                    if (java.nio.file.Files.exists(altPath)) {
                        filePath = altPath;
                    } else {
                        return ResponseEntity.notFound().build();
                    }
                } else {
                    return ResponseEntity.notFound().build();
                }
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = "application/octet-stream";
                String lowerFilename = decodedFilename.toLowerCase();
                if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (lowerFilename.endsWith(".png")) {
                    contentType = "image/png";
                } else if (lowerFilename.endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (lowerFilename.endsWith(".webp")) {
                    contentType = "image/webp";
                }

                log.info("✅ File found, serving with content-type: {}", contentType);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + decodedFilename + "\"")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                        .body(resource);
            } else {
                log.warn("❌ File not readable: {}", filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("❌ Error serving file: {}/{}", reclamationId, filename, e);
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get reclamation statistics for admin dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            List<Reclamation> allReclamations = reclamationService.getAllReclamations();

            Map<String, Object> stats = new HashMap<>();
            stats.put("total", allReclamations.size());
            stats.put("open", allReclamations.stream().filter(r -> r.getStatus() == ReclamationStatus.OPEN).count());
            stats.put("inReview",
                    allReclamations.stream().filter(r -> r.getStatus() == ReclamationStatus.IN_REVIEW).count());
            stats.put("resolved",
                    allReclamations.stream().filter(r -> r.getStatus() == ReclamationStatus.RESOLVED).count());
            stats.put("rejected",
                    allReclamations.stream().filter(r -> r.getStatus() == ReclamationStatus.REJECTED).count());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching statistics", e);
            return ResponseEntity.status(500).build();
        }
    }

    private static class ResolveReclamationRequest {
        private String resolutionNotes;
        private boolean approved;

        public String getResolutionNotes() {
            return resolutionNotes;
        }

        public void setResolutionNotes(String resolutionNotes) {
            this.resolutionNotes = resolutionNotes;
        }

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }
    }

    private static class RejectReclamationRequest {
        private String rejectionNotes;

        public String getRejectionNotes() {
            return rejectionNotes;
        }

        public void setRejectionNotes(String rejectionNotes) {
            this.rejectionNotes = rejectionNotes;
        }
    }
}
