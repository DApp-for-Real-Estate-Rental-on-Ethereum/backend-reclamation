package ma.fstt.reclamationservice.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.file-storage.path:./uploads/reclamations}")
    private String storagePath;

    public List<String> saveFiles(Long reclamationId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        Path uploadDir = Paths.get(storagePath, String.valueOf(reclamationId));
        Files.createDirectories(uploadDir);

        List<String> savedPaths = new ArrayList<>();
        int order = 1;

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            Path filePath = uploadDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = Paths.get(String.valueOf(reclamationId), uniqueFilename).toString();
            savedPaths.add(relativePath);

            order++;
        }

        return savedPaths;
    }

    public void deleteFiles(Long reclamationId) {
        try {
            Path uploadDir = Paths.get(storagePath, String.valueOf(reclamationId));
            if (Files.exists(uploadDir)) {
                Files.walk(uploadDir)
                        .sorted((a, b) -> -a.compareTo(b)) // Delete files first, then directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                            }
                        });
            }
        } catch (IOException e) {
        }
    }

    public Path getFilePath(String relativePath) {
        return Paths.get(storagePath, relativePath);
    }

    public void deleteFile(Long reclamationId, String relativePath) throws IOException {
        try {
            String filename = relativePath.contains("/") 
                ? relativePath.substring(relativePath.lastIndexOf("/") + 1)
                : relativePath;
            
            Path filePath = Paths.get(storagePath, String.valueOf(reclamationId), filename);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            throw e;
        }
    }
}

