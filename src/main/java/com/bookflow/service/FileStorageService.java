package com.bookflow.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Handles all file I/O for uploaded resources.
 *
 * Security principles applied here:
 *  - The original filename is NEVER used as the storage path.
 *  - A UUID-based name is generated server-side for every stored file.
 *  - Path traversal is blocked by sanitising the extension and resolving
 *    the target path against the configured root, then verifying it stays
 *    inside that root.
 *  - Only PDF, DOC, and DOCX are accepted.
 *  - The upload directory is outside the public static web root.
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

    @Value("${bookflow.upload.dir}")
    private String uploadDirProperty;

    private Path uploadRoot;

    @PostConstruct
    public void init() {
        uploadRoot = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create upload directory: " + uploadRoot, e);
        }
    }

    /**
     * Validates and stores the file. Returns the server-generated filename
     * (not the original name) which is persisted in the database.
     *
     * @throws IllegalArgumentException for rejected file types or empty files
     * @throws IOException              for filesystem errors
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a file to upload.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds the 20 MB limit.");
        }

        String extension = extractAndValidateExtension(file.getOriginalFilename());

        // Generate a UUID-based filename — completely decoupled from the original name.
        String storedFilename = UUID.randomUUID().toString() + "." + extension;

        // Resolve against the upload root and verify no traversal escapes the directory.
        Path targetPath = uploadRoot.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid file path detected.");
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return storedFilename;
    }

    /**
     * Sanitises the original filename's extension and checks it against the allowlist.
     * Returns the lowercase extension without the dot.
     */
    private String extractAndValidateExtension(String originalFilename) {
        // StringUtils.getFilenameExtension strips path components before checking.
        String ext = StringUtils.getFilenameExtension(originalFilename);
        if (ext == null || ext.isBlank()) {
            throw new IllegalArgumentException(
                    "Uploaded file has no extension. Only PDF, DOC, and DOCX are accepted.");
        }
        String lowerExt = ext.toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(lowerExt)) {
            throw new IllegalArgumentException(
                    "File type '." + lowerExt + "' is not allowed. "
                    + "Only PDF, DOC, and DOCX files are accepted.");
        }
        return lowerExt;
    }

    /**
     * Returns a sanitised display name derived from the original filename.
     * This is stored as metadata only — never used as a storage path.
     */
    public String sanitiseOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        // Keep only the filename part (no directory separators).
        String name = Paths.get(originalFilename).getFileName().toString();
        // Strip any characters that are not alphanumeric, dots, hyphens, or underscores.
        return name.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    /**
     * Resolves a stored filename to its absolute Path and verifies it stays
     * inside the upload root — preventing any path traversal attack.
     *
     * The storedFilename comes from our own database (a UUID we generated),
     * so it should always be safe, but we validate it regardless.
     *
     * @param storedFilename the UUID-based filename stored in the database
     * @return the resolved, normalised Path inside uploadRoot
     * @throws IllegalArgumentException if the resolved path escapes uploadRoot
     * @throws IOException              if the file does not exist
     */
    public Path resolveFilePath(String storedFilename) throws IOException {
        // Resolve and normalise — this collapses any ".." sequences.
        Path resolved = uploadRoot.resolve(storedFilename).normalize();

        // Path traversal guard: the resolved path must still start with uploadRoot.
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException(
                    "Invalid file path: path traversal detected.");
        }

        if (!Files.exists(resolved)) {
            throw new IOException("File not found: " + storedFilename);
        }

        return resolved;
    }
}
