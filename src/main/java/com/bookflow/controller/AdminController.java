package com.bookflow.controller;

import com.bookflow.dto.RejectDto;
import com.bookflow.entity.Resource;
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
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * Handles all admin pages and actions.
 *
 * GET /admin          — admin login page (public — no @PreAuthorize, handled by filter chain)
 * Everything else     — requires ROLE_ADMIN via class-level @PreAuthorize AND the
 *                       admin filter chain's URL rules, giving two independent layers.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ResourceService resourceService;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final FileStorageService fileStorageService;

    // ----------------------------------------------------------------
    // Admin login page — GET /admin
    // This handler is intentionally NOT annotated with @PreAuthorize so
    // that unauthenticated visitors can reach the login form.
    // Visiting this page does NOT grant any privileges.
    // ----------------------------------------------------------------

    @GetMapping("")
    @PreAuthorize("permitAll()")
    public String adminLoginPage(
            @AuthenticationPrincipal UserDetails userDetails) {

        // If already authenticated as ADMIN, skip the login page.
        if (userDetails != null) {
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isAdmin) {
                return "redirect:/admin/dashboard";
            }
        }
        return "admin/login";
    }

    // ----------------------------------------------------------------
    // Admin dashboard
    // ----------------------------------------------------------------

    @GetMapping({"/", "/dashboard"})
    @PreAuthorize("hasRole('ADMIN')")
    public String dashboard(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        User admin = resolveAdmin(userDetails);
        int pendingCount = resourceService.findPending().size();

        model.addAttribute("admin", admin);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("pageTitle", "Admin Dashboard");
        return "admin/dashboard";
    }

    // ----------------------------------------------------------------
    // Pending resource queue — GET /admin/resources/pending
    // ----------------------------------------------------------------

    @GetMapping("/resources/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public String pendingResources(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        User admin = resolveAdmin(userDetails);
        List<Resource> pendingResources = resourceService.findPending();

        model.addAttribute("admin", admin);
        model.addAttribute("pendingResources", pendingResources);
        model.addAttribute("rejectDto", new RejectDto());
        model.addAttribute("pageTitle", "Pending Resources");
        return "admin/pending-resources";
    }

    // ----------------------------------------------------------------
    // Approve — POST /admin/resources/{id}/approve
    // ----------------------------------------------------------------

    @PostMapping("/resources/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public String approveResource(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        User admin = resolveAdmin(userDetails);

        try {
            resourceService.approve(id, admin);
            redirectAttributes.addFlashAttribute("actionSuccess",
                    "Resource approved successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        }

        return "redirect:/admin/resources/pending";
    }

    // ----------------------------------------------------------------
    // Reject — POST /admin/resources/{id}/reject
    // ----------------------------------------------------------------

    @PostMapping("/resources/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public String rejectResource(
            @PathVariable Long id,
            @Valid @ModelAttribute("rejectDto") RejectDto rejectDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {

        // If the rejection reason is blank, redisplay the pending list with errors.
        if (bindingResult.hasErrors()) {
            User admin = resolveAdmin(userDetails);
            model.addAttribute("admin", admin);
            model.addAttribute("pendingResources", resourceService.findPending());
            model.addAttribute("rejectErrorId", id);   // highlights which row had the error
            model.addAttribute("pageTitle", "Pending Resources");
            return "admin/pending-resources";
        }

        User admin = resolveAdmin(userDetails);

        try {
            resourceService.reject(id, rejectDto.getRejectionReason(), admin);
            redirectAttributes.addFlashAttribute("actionSuccess",
                    "Resource rejected.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        }

        return "redirect:/admin/resources/pending";
    }

    // ----------------------------------------------------------------
    // Serve resource file — GET /admin/resources/{id}/file
    //
    // Streams the uploaded file to the admin for review.
    // PDFs are served inline so the browser can display them directly.
    // DOC/DOCX are served as attachments (download).
    //
    // The physical path is NEVER taken from the request — only the
    // resource ID is accepted; the stored UUID filename comes from
    // the database and is validated against the upload root before use.
    // ----------------------------------------------------------------

    @GetMapping("/resources/{id}/file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.core.io.Resource> serveResourceFile(
            @PathVariable Long id) {

        // Look up the resource row — 404 if it doesn't exist.
        Resource resource = resourceRepository.findById(id).orElse(null);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath;
        try {
            // resolveFilePath validates the path stays inside uploadRoot.
            filePath = fileStorageService.resolveFilePath(resource.getFilePath());
        } catch (IOException e) {
            // Physical file missing.
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            // Path traversal attempt detected (should never happen with UUID names).
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        org.springframework.core.io.Resource fileResource = new PathResource(filePath);

        // Determine Content-Type from the stored extension.
        String storedName = resource.getFilePath().toLowerCase();
        MediaType mediaType;
        if (storedName.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else if (storedName.endsWith(".docx")) {
            mediaType = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else {
            // .doc
            mediaType = MediaType.parseMediaType("application/msword");
        }

        // Use the sanitised original filename as the download name, falling back
        // to the stored UUID name. The browser never sees the raw UUID path.
        String downloadName = (resource.getOriginalFilename() != null
                && !resource.getOriginalFilename().isBlank())
                ? resource.getOriginalFilename()
                : resource.getFilePath();

        HttpHeaders headers = new HttpHeaders();
        if (storedName.endsWith(".pdf")) {
            // PDFs: inline so the browser opens them in its viewer.
            headers.setContentDisposition(
                    ContentDisposition.inline().filename(downloadName).build());
        } else {
            // DOC/DOCX: attachment so they are downloaded.
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(downloadName).build());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(fileResource);
    }

    // ----------------------------------------------------------------
    // Private helper
    // ----------------------------------------------------------------

    private User resolveAdmin(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated admin not found in database"));
    }
}
