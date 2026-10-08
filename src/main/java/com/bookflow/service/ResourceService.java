package com.bookflow.service;

import com.bookflow.dto.ResourceFilterDto;
import com.bookflow.dto.ResourceUploadDto;
import com.bookflow.entity.Resource;
import com.bookflow.entity.ResourceStatus;
import com.bookflow.entity.User;
import com.bookflow.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final FileStorageService fileStorageService;

    // ----------------------------------------------------------------
    // User-facing operations
    // ----------------------------------------------------------------

    /**
     * Persists a new resource upload.
     *
     * The uploader is always set server-side from the authenticated principal.
     * Status is always forced to PENDING regardless of any client input.
     * The verifiedBy and verifiedAt fields are never touched here.
     *
     * @throws IllegalArgumentException if the file is missing, empty, or has a disallowed type
     * @throws IOException              if the file cannot be written to disk
     */
    @Transactional
    public void upload(ResourceUploadDto dto, User uploader) throws IOException {

    if (dto.getFiles() == null || dto.getFiles().isEmpty()) {
        throw new IllegalArgumentException("Please select at least one file to upload.");
    }

    for (MultipartFile file : dto.getFiles()) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "One of the selected files is empty.");
        }

        String storedFilename = fileStorageService.store(file);

        String safeOriginalName = fileStorageService
                .sanitiseOriginalFilename(file.getOriginalFilename());

        Resource resource = new Resource();

        resource.setTitle(dto.getTitle().trim());
        resource.setDescription(
                dto.getDescription() != null
                        ? dto.getDescription().trim()
                        : null);
        resource.setType(dto.getType());
        resource.setBranch(dto.getBranch().trim());
        resource.setSubject(dto.getSubject().trim());
        resource.setYear(dto.getYear());
        resource.setSemester(dto.getSemester());

        resource.setFilePath(storedFilename);
        resource.setOriginalFilename(safeOriginalName);

        // Server-side enforced values — never from the form.
        resource.setUploadedBy(uploader);
        resource.setStatus(ResourceStatus.PENDING);

        resourceRepository.save(resource);
    }
}

    /**
     * Returns all resources uploaded by the given user, newest first.
     */
    public List<Resource> findByUploader(User uploader) {
        return resourceRepository.findByUploadedByOrderByCreatedAtDesc(uploader);
    }

    /**
     * Returns APPROVED resources matching the given filter/search criteria.
     *
     * status = APPROVED is enforced at the repository query level — it cannot
     * be overridden by any value in ResourceFilterDto.
     * Blank/null filter fields are treated as "no restriction on this field".
     */
    public List<Resource> findApproved(ResourceFilterDto filter) {
        String keyword  = blankToNull(filter.getKeyword());
        String branch   = blankToNull(filter.getBranch());
        String subject  = blankToNull(filter.getSubject());

        return resourceRepository.findApproved(
                keyword,
                filter.getType(),
                branch,
                subject,
                filter.getYear(),
                filter.getSemester()
        );
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ----------------------------------------------------------------
    // Admin upload — publishes directly as APPROVED
    // ----------------------------------------------------------------

    /**
     * Admin-direct resource upload. The resource is published immediately as
     * APPROVED — no pending review step is needed for admin-uploaded content.
     * The admin is set as both uploader and verifier.
     *
     * USER uploads continue to use {@link #upload(ResourceUploadDto, User)}
     * which enforces PENDING status.
     */
    @Transactional
    public void uploadAsAdmin(
        ResourceUploadDto dto,
        User admin) throws IOException {

    if (dto.getFiles() == null || dto.getFiles().isEmpty()) {
        throw new IllegalArgumentException("Please select at least one file to upload.");
    }

    for (MultipartFile file : dto.getFiles()) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "One of the selected files is empty.");
        }

        String storedFilename = fileStorageService.store(file);

        String safeOriginalName = fileStorageService
                .sanitiseOriginalFilename(file.getOriginalFilename());

        Resource resource = new Resource();

        resource.setTitle(dto.getTitle().trim());
        resource.setDescription(
                dto.getDescription() != null
                        ? dto.getDescription().trim()
                        : null);
        resource.setType(dto.getType());
        resource.setBranch(dto.getBranch().trim());
        resource.setSubject(dto.getSubject().trim());
        resource.setYear(dto.getYear());
        resource.setSemester(dto.getSemester());

        resource.setFilePath(storedFilename);
        resource.setOriginalFilename(safeOriginalName);

        resource.setUploadedBy(admin);

        // Admin uploads are pre-approved.
        resource.setStatus(ResourceStatus.APPROVED);
        resource.setVerifiedBy(admin);
        resource.setVerifiedAt(LocalDateTime.now());

        resourceRepository.save(resource);
    }
}

    // ----------------------------------------------------------------
    // Dashboard statistics
    // ----------------------------------------------------------------

    public long countPending()  { return resourceRepository.countByStatus(ResourceStatus.PENDING);  }
    public long countApproved() { return resourceRepository.countByStatus(ResourceStatus.APPROVED); }
    public long countRejected() { return resourceRepository.countByStatus(ResourceStatus.REJECTED); }
    public long countTotal()    { return resourceRepository.count(); }

    /** Counts APPROVED resources of a specific type — used by the library category cards. */
    public long countApprovedByType(com.bookflow.entity.ResourceType type) {
        return resourceRepository.countByStatusAndType(ResourceStatus.APPROVED, type);
    }

    /** Returns the N most recently submitted resources for the admin activity feed. */
    public List<Resource> findRecent(int limit) {
        return resourceRepository.findRecentResources(limit);
    }

    /**
     * Returns all PENDING resources in submission order (oldest first),
     * so admins review them in the order they were uploaded.
     */
    public List<Resource> findPending() {
        return resourceRepository.findByStatusOrderByCreatedAtAsc(ResourceStatus.PENDING);
    }

    /**
     * Approves a PENDING resource.
     *
     * Only PENDING resources may be approved. Attempting to approve a resource
     * that is already APPROVED or REJECTED throws IllegalStateException.
     *
     * @param resourceId the id of the resource to approve
     * @param admin      the authenticated admin performing the action
     * @throws IllegalArgumentException if the resource does not exist
     * @throws IllegalStateException    if the resource is not PENDING
     */
    @Transactional
    public void approve(Long resourceId, User admin) {
        Resource resource = findResourceById(resourceId);
        requirePending(resource);

        resource.setStatus(ResourceStatus.APPROVED);
        resource.setVerifiedBy(admin);
        resource.setVerifiedAt(LocalDateTime.now());
        resource.setRejectionReason(null);

        resourceRepository.save(resource);
    }

    /**
     * Rejects a PENDING resource with a mandatory reason.
     *
     * Only PENDING resources may be rejected. Attempting to reject a resource
     * that is already APPROVED or REJECTED throws IllegalStateException.
     *
     * @param resourceId      the id of the resource to reject
     * @param rejectionReason non-blank reason shown to the uploader
     * @param admin           the authenticated admin performing the action
     * @throws IllegalArgumentException if the resource does not exist
     * @throws IllegalStateException    if the resource is not PENDING
     */
    @Transactional
    public void reject(Long resourceId, String rejectionReason, User admin) {
        Resource resource = findResourceById(resourceId);
        requirePending(resource);

        resource.setStatus(ResourceStatus.REJECTED);
        resource.setVerifiedBy(admin);
        resource.setVerifiedAt(LocalDateTime.now());
        resource.setRejectionReason(rejectionReason.trim());

        resourceRepository.save(resource);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private Resource findResourceById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resource not found with id: " + id));
    }

    /**
     * Ensures the resource is still PENDING. Admins cannot re-verify a
     * resource that has already been actioned.
     */
    private void requirePending(Resource resource) {
        if (resource.getStatus() != ResourceStatus.PENDING) {
            throw new IllegalStateException(
                    "Resource '" + resource.getTitle() + "' has already been "
                    + resource.getStatus().name().toLowerCase()
                    + " and cannot be actioned again.");
        }
    }
}
