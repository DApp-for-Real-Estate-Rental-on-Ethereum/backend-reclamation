package ma.fstt.reclamationservice.api.controller;

import lombok.RequiredArgsConstructor;
import ma.fstt.reclamationservice.core.service.ReclamationService;
import ma.fstt.reclamationservice.domain.entity.Reclamation;
import ma.fstt.reclamationservice.domain.entity.ReclamationAttachment;
import ma.fstt.reclamationservice.domain.entity.ReclamationType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reclamations")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000"}, allowCredentials = "true")
@RequiredArgsConstructor
public class ReclamationController {

    private final ReclamationService reclamationService;

    @PostMapping(value = "/create", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> createReclamationWithImages(
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("userId") Long userId,
            @RequestParam("complainantRole") String complainantRole,
            @RequestParam("reclamationType") String reclamationType,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (files != null && files.size() > 3) {
                response.put("status", "error");
                response.put("message", "Maximum 3 images allowed");
                return ResponseEntity.badRequest().body(response);
            }

            Reclamation.ComplainantRole role = Reclamation.ComplainantRole.valueOf(complainantRole);
            ReclamationType type = ReclamationType.valueOf(reclamationType);

            Reclamation reclamation = reclamationService.createReclamation(
                    bookingId, userId, role, type, title, description
            );

            if (files != null && !files.isEmpty()) {
                try {
                    reclamationService.saveAttachments(reclamation.getId(), files);
                } catch (Exception e) {
                }
            }

            response.put("status", "success");
            response.put("message", "Reclamation created successfully");
            response.put("reclamationId", reclamation.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to create reclamation: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/{reclamationId}/attachments")
    public ResponseEntity<Map<String, Object>> uploadAttachments(
            @PathVariable Long reclamationId,
            @RequestParam("files") List<MultipartFile> files) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (files == null || files.isEmpty()) {
                response.put("status", "error");
                response.put("message", "No files provided");
                return ResponseEntity.badRequest().body(response);
            }

            if (files.size() > 3) {
                response.put("status", "error");
                response.put("message", "Maximum 3 images allowed");
                return ResponseEntity.badRequest().body(response);
            }

            reclamationService.saveAttachments(reclamationId, files);

            response.put("status", "success");
            response.put("message", "Files uploaded successfully");
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("status", "error");
            response.put("message", "Failed to upload files: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to upload files: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/my-complaints")
    public ResponseEntity<List<Reclamation>> getMyComplaints(@RequestParam Long userId) {
        try {
            List<Reclamation> reclamations = reclamationService.getReclamationsByComplainantId(userId);
            return ResponseEntity.ok(reclamations);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/against-me")
    public ResponseEntity<List<Reclamation>> getComplaintsAgainstMe(@RequestParam Long userId) {
        try {
            List<Reclamation> reclamations = reclamationService.getReclamationsByTargetUserId(userId);
            if (reclamations.isEmpty()) {
                reclamationService.getAllReclamations();
            }
            return ResponseEntity.ok(reclamations);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{reclamationId}")
    public ResponseEntity<Reclamation> getReclamationById(@PathVariable Long reclamationId) {
        try {
            Reclamation reclamation = reclamationService.getReclamationById(reclamationId);
            return ResponseEntity.ok(reclamation);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/booking/{bookingId}/complainant/{complainantId}")
    public ResponseEntity<Reclamation> getReclamationByBookingIdAndComplainant(
            @PathVariable Long bookingId,
            @PathVariable Long complainantId) {
        try {
            Reclamation reclamation = reclamationService.getReclamationByBookingIdAndComplainantId(bookingId, complainantId);
            if (reclamation == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(reclamation);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{reclamationId}/attachments")
    public ResponseEntity<List<ReclamationAttachment>> getAttachments(@PathVariable Long reclamationId) {
        try {
            List<ReclamationAttachment> attachments = reclamationService.getAttachmentsByReclamationId(reclamationId);
            return ResponseEntity.ok(attachments);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/files/{reclamationId}/{filename:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable Long reclamationId,
            @PathVariable String filename) {
        try {
            String decodedFilename = URLDecoder.decode(filename, "UTF-8");
            Path filePath = Paths.get("./uploads/reclamations", String.valueOf(reclamationId), decodedFilename).normalize();
            
            if (!Files.exists(filePath)) {
                if (!filename.equals(decodedFilename)) {
                    Path altPath = Paths.get("./uploads/reclamations", String.valueOf(reclamationId), filename).normalize();
                    if (Files.exists(altPath)) {
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
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + decodedFilename + "\"")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/{reclamationId}")
    public ResponseEntity<Map<String, Object>> deleteReclamation(
            @PathVariable Long reclamationId,
            @RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            reclamationService.deleteReclamation(reclamationId, userId);
            response.put("status", "success");
            response.put("message", "Reclamation deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to delete reclamation: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping(value = "/{reclamationId}", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> updateReclamation(
            @PathVariable Long reclamationId,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        Map<String, Object> response = new HashMap<>();
        try {
            Reclamation updated = reclamationService.updateReclamation(reclamationId, userId, title, description, files);
            response.put("status", "success");
            response.put("message", "Reclamation updated successfully");
            response.put("reclamation", updated);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to update reclamation: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/user/{userId}/phone")
    public ResponseEntity<Map<String, Object>> getUserPhoneNumber(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String phoneNumber = reclamationService.getUserPhoneNumber(userId);
            response.put("status", "success");
            response.put("phoneNumber", phoneNumber);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to fetch phone number: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
