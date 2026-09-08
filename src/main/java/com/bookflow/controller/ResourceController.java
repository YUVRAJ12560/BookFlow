package com.bookflow.controller;

import com.bookflow.dto.ResourceFilterDto;
import com.bookflow.dto.ResourceUploadDto;
import com.bookflow.entity.Resource;
import com.bookflow.entity.ResourceStatus;
import com.bookflow.entity.ResourceType;
import com.bookflow.entity.User;
import com.bookflow.repository.ResourceRepository;
import com.bookflow.repository.UserRepository;
import com.bookflow.service.FileStorageService;
import com.bookflow.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequestMapping("/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final FileStorageService fileStorageService;

    // ----------------------------------------------------------------
    // Public Resource Library — GET /resources
    // Accessible to unauthenticated visitors.
    // Only APPROVED resources are returned — enforced server-side.
    // ----------------------------------------------------------------

    @GetMapping("")
    public String resourceLibrary(ResourceFilterDto filter, Model model) {
        List<Resource> resources = resourceService.findApproved(filter);

        model.addAttribute("resources", resources);
        model.addAttribute("filter", filter);
        model.addAttribute("resourceTypes", ResourceType.values());
        model.addAttribute("pageTitle", "Resource Library");
        return "resources/library";
    }

    // ----------------------------------------------------------------
    // Public Resource Download — GET /resources/{id}/download
    //
    // Publicly accessible — APPROVED resources are public content.
    //
    // Security invariants enforced server-side (never from the request):
    //   1. Resource must exist in the database.
    //   2. Resource status must be exactly APPROVED — PENDING and REJECTED
    //      resources are refused with 404 so as not to reveal their existence.
    //   3. Physical path is resolved through FileStorageService.resolveFilePath()
    //      which contains the path-traversal guard (startsWith uploadRoot check).
    //   4. Resource status is read from the database, never from the browser.
    // ----------------------------------------------------------------

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(
            @PathVariable Long id) {

        // Step 1 — look up the resource row.
        Resource resource = resourceRepository.findById(id).orElse(null);

        // Step 2 — resource must exist AND be APPROVED.
        // We return 404 for both "not found" and "not approved" to avoid
        // leaking information about the existence of pending/rejected resources.
        if (resource == null || resource.getStatus() != ResourceStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }

        // Step 3 — resolve the physical file through the validated service method.
        Path filePath;
        try {
            filePath = fileStorageService.resolveFilePath(resource.getFilePath());
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            // Path traversal detected — should never occur with UUID filenames.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        org.springframework.core.io.Resource fileResource = new PathResource(filePath);

        // Step 4 — determine Content-Type from the stored (server-generated) extension.
        String storedName = resource.getFilePath().toLowerCase();
        MediaType mediaType;
        if (storedName.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else if (storedName.endsWith(".docx")) {
            mediaType = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else {
            mediaType = MediaType.parseMediaType("application/msword");
        }

        // Step 5 — use the sanitised original filename for Content-Disposition.
        // The browser sees the original name, not the internal UUID.
        String downloadName = (resource.getOriginalFilename() != null
                && !resource.getOriginalFilename().isBlank())
                ? resource.getOriginalFilename()
                : resource.getFilePath();

        HttpHeaders headers = new HttpHeaders();
        if (storedName.endsWith(".pdf")) {
            // PDFs open inline in the browser viewer.
            headers.setContentDisposition(
                    ContentDisposition.inline().filename(downloadName).build());
        } else {
            // DOC/DOCX are downloaded as attachments.
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(downloadName).build());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(fileResource);
    }

    // ----------------------------------------------------------------
    // Upload form — GET /resources/upload
    // ----------------------------------------------------------------

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("resourceUploadDto", new ResourceUploadDto());
        model.addAttribute("resourceTypes", ResourceType.values());
        model.addAttribute("pageTitle", "Upload Resource");
        return "resources/upload";
    }

    // ----------------------------------------------------------------
    // Upload submission — POST /resources/upload
    // ----------------------------------------------------------------

    @PostMapping("/upload")
    public String processUpload(
            @Valid @ModelAttribute("resourceUploadDto") ResourceUploadDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Re-populate the type list so the form can redisplay on error.
        model.addAttribute("resourceTypes", ResourceType.values());

        // Bean validation errors (blank title, missing type, etc.)
        if (bindingResult.hasErrors()) {
            return "resources/upload";
        }

        // Resolve the authenticated user entity from the database.
        User uploader = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        try {
            resourceService.upload(dto, uploader);
        } catch (IllegalArgumentException e) {
            // File validation error (wrong type, empty file, oversized, etc.)
            model.addAttribute("fileError", e.getMessage());
            return "resources/upload";
        } catch (Exception e) {
            model.addAttribute("uploadError",
                    "An unexpected error occurred while saving your file. Please try again.");
            return "resources/upload";
        }

        redirectAttributes.addFlashAttribute("uploadSuccess",
                "Your resource has been submitted and is pending review by an administrator.");
        return "redirect:/resources/my";
    }

    // ----------------------------------------------------------------
    // My Resources — GET /resources/my
    // ----------------------------------------------------------------

    @GetMapping("/my")
    public String myResources(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        User uploader = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        List<Resource> resources = resourceService.findByUploader(uploader);
        model.addAttribute("resources", resources);
        model.addAttribute("user", uploader);
        model.addAttribute("pageTitle", "My Uploads");
        return "resources/my-resources";
    }
}
