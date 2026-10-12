/* ==========================================
   BookFlow — Main JavaScript (v5)
   ========================================== */

document.addEventListener('DOMContentLoaded', function () {

    // ── Explicitly initialize ALL Bootstrap dropdowns ───────────
    if (typeof bootstrap !== 'undefined' && bootstrap.Dropdown) {
        document.querySelectorAll('[data-bs-toggle="dropdown"]').forEach(function (el) {
            bootstrap.Dropdown.getOrCreateInstance(el);
        });
    }

    // ── Auto-dismiss alert messages ─────────────────────────────
    if (typeof bootstrap !== 'undefined' && bootstrap.Alert) {
        document.querySelectorAll('.alert.auto-dismiss').forEach(function (alert) {
            setTimeout(function () {
                try {
                    var bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
                    bsAlert.close();
                } catch (e) {}
            }, 4500);
        });
    }

    // ── Password visibility toggles ─────────────────────────────
    document.querySelectorAll('.bf-pass-toggle').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var targetId = btn.getAttribute('data-target');
            var input = document.getElementById(targetId);
            if (!input) return;
            var icon = btn.querySelector('i');
            if (input.type === 'password') {
                input.type = 'text';
                if (icon) { icon.classList.remove('bi-eye'); icon.classList.add('bi-eye-slash'); }
                btn.setAttribute('aria-label', 'Hide password');
            } else {
                input.type = 'password';
                if (icon) { icon.classList.remove('bi-eye-slash'); icon.classList.add('bi-eye'); }
                btn.setAttribute('aria-label', 'Show password');
            }
        });
    });

    // ── Multi-file upload manager with individual file removal ──
    initFileUploadManager();

    // ── Sticky navbar scroll shadow ──────────────────────────────
    var navbar = document.querySelector('.bf-navbar');
    if (navbar) {
        window.addEventListener('scroll', function () {
            if (window.scrollY > 8) {
                navbar.style.boxShadow = '0 2px 12px rgba(0,0,0,.28)';
            } else {
                navbar.style.boxShadow = '0 1px 4px rgba(0,0,0,.18)';
            }
        }, { passive: true });
    }

    // ── Prevent double-submission on primary upload/submit forms ─
    document.querySelectorAll('form').forEach(function (form) {
        var submitBtn = form.querySelector('button[type="submit"].btn-primary, button[type="submit"].adm-submit-btn');
        if (!submitBtn) return;
        var action = (form.getAttribute('action') || '').toLowerCase();
        if (action.indexOf('logout') !== -1) return;
        if (form.getAttribute('method') === 'get') return;

        form.addEventListener('submit', function () {
            submitBtn.disabled = true;
            submitBtn.style.opacity = '0.7';
            setTimeout(function () {
                submitBtn.disabled = false;
                submitBtn.style.opacity = '';
            }, 8000);
        });
    });

});

/**
 * Robust Multi-File Upload Manager
 * Supports:
 * - Adding multiple files sequentially without losing previous choices
 * - Removing specific individual files via trash/X button
 * - Immediate DataTransfer synchronization so MultipartFile collection matches DOM
 * - Duplicate file prevention
 * - Format (.pdf, .doc, .docx) and size (<=20MB) validation with user-visible alert
 * - Accessible keyboard and mobile controls
 */
function initFileUploadManager() {
    var uploadZone = document.getElementById('uploadZone') ||
                     document.getElementById('adminUploadZone') ||
                     document.querySelector('.bf-upload-zone') ||
                     document.querySelector('.adm-upload-zone');

    var fileInput = document.getElementById('file');
    var fileList = document.getElementById('file-list');
    var filesContainer = document.getElementById('selected-files-container');
    var countLabel = document.getElementById('file-count-label');
    var errorAlert = document.getElementById('file-error-alert');
    var errorText = document.getElementById('file-error-text');
    var clearAllBtn = document.getElementById('clearAllFilesBtn');

    if (!uploadZone || !fileInput || !fileList) return;

    var selectedFiles = [];
    var MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB
    var ALLOWED_EXTS = ['.pdf', '.doc', '.docx'];

    function formatBytes(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        var k = 1024;
        var sizes = ['B', 'KB', 'MB', 'GB'];
        var i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    function getFileKey(file) {
        return (file.name || '') + '__' + (file.size || 0) + '__' + (file.lastModified || 0);
    }

    function validateFile(file) {
        var name = file.name || '';
        var lastDot = name.lastIndexOf('.');
        if (lastDot === -1) {
            return 'File "' + name + '" has no extension. Only PDF, DOC, and DOCX files are allowed.';
        }
        var ext = name.substring(lastDot).toLowerCase();
        if (ALLOWED_EXTS.indexOf(ext) === -1) {
            return 'File "' + name + '" is not supported. Only PDF, DOC, and DOCX files are accepted.';
        }
        if (file.size > MAX_FILE_SIZE) {
            return 'File "' + name + '" (' + formatBytes(file.size) + ') exceeds the 20 MB per-file limit.';
        }
        return null;
    }

    function syncInputFiles() {
        try {
            var dt = new DataTransfer();
            for (var i = 0; i < selectedFiles.length; i++) {
                dt.items.add(selectedFiles[i]);
            }
            fileInput.files = dt.files;
        } catch (err) {
            console.warn('DataTransfer sync warning:', err);
        }
        renderFileList();
    }

    function renderFileList() {
        fileList.innerHTML = '';
        if (selectedFiles.length === 0) {
            if (filesContainer) filesContainer.style.display = 'none';
            var fallbackLbl = document.getElementById('file-label');
            if (fallbackLbl) { fallbackLbl.textContent = ''; }
            return;
        }

        if (filesContainer) filesContainer.style.display = 'block';

        var totalSize = 0;
        for (var s = 0; s < selectedFiles.length; s++) {
            totalSize += selectedFiles[s].size;
        }

        if (countLabel) {
            countLabel.textContent = selectedFiles.length +
                (selectedFiles.length === 1 ? ' file attached (' : ' files attached (') +
                formatBytes(totalSize) + ')';
        }

        selectedFiles.forEach(function (file, index) {
            var ext = (file.name.split('.').pop() || '').toLowerCase();
            var iconClass = 'bi-file-earmark-text';
            var badgeClass = 'bg-primary-subtle text-primary';

            if (ext === 'pdf') {
                iconClass = 'bi-file-earmark-pdf';
                badgeClass = 'bg-danger-subtle text-danger';
            } else if (ext === 'doc' || ext === 'docx') {
                iconClass = 'bi-file-earmark-word';
                badgeClass = 'bg-primary-subtle text-primary';
            }

            var li = document.createElement('li');
            li.className = 'bf-selected-file-card';
            li.innerHTML =
                '<div class="d-flex align-items-center gap-2 min-w-0 flex-grow-1" style="overflow:hidden;">' +
                    '<span class="bf-file-ext-badge ' + badgeClass + '">' +
                        '<i class="bi ' + iconClass + '" aria-hidden="true"></i>' +
                    '</span>' +
                    '<div class="min-w-0 flex-grow-1" style="overflow:hidden;">' +
                        '<div class="bf-file-name text-truncate fw-medium" title="' + escapeHtml(file.name) + '">' +
                            escapeHtml(file.name) +
                        '</div>' +
                        '<div class="bf-file-size text-muted small">' +
                            formatBytes(file.size) +
                        '</div>' +
                    '</div>' +
                '</div>' +
                '<button type="button" class="bf-file-remove-btn" ' +
                        'data-file-index="' + index + '" ' +
                        'aria-label="Remove ' + escapeHtml(file.name) + '" ' +
                        'title="Remove file">' +
                    '<i class="bi bi-trash3" aria-hidden="true"></i>' +
                '</button>';

            fileList.appendChild(li);
        });

        // Bind remove button listeners
        fileList.querySelectorAll('.bf-file-remove-btn').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var idx = parseInt(btn.getAttribute('data-file-index'), 10);
                removeFileAt(idx);
            });
        });
    }

    function addFiles(newFiles) {
        if (!newFiles || newFiles.length === 0) return;
        var errors = [];
        var existingKeys = {};
        for (var i = 0; i < selectedFiles.length; i++) {
            existingKeys[getFileKey(selectedFiles[i])] = true;
        }

        for (var j = 0; j < newFiles.length; j++) {
            var file = newFiles[j];
            var error = validateFile(file);
            if (error) {
                errors.push(error);
                continue;
            }
            var key = getFileKey(file);
            if (existingKeys[key]) {
                continue; // Skip duplicate
            }
            existingKeys[key] = true;
            selectedFiles.push(file);
        }

        if (errors.length > 0) {
            if (errorText && errorAlert) {
                errorText.innerHTML = errors.map(escapeHtml).join('<br>');
                errorAlert.style.display = 'flex';
            }
        } else if (errorAlert) {
            errorAlert.style.display = 'none';
        }

        syncInputFiles();
    }

    function removeFileAt(index) {
        if (index >= 0 && index < selectedFiles.length) {
            selectedFiles.splice(index, 1);
            if (errorAlert) errorAlert.style.display = 'none';
            syncInputFiles();
        }
    }

    function clearAllFiles() {
        selectedFiles = [];
        if (errorAlert) errorAlert.style.display = 'none';
        try {
            fileInput.value = '';
        } catch (e) {}
        syncInputFiles();
    }

    if (clearAllBtn) {
        clearAllBtn.addEventListener('click', function (e) {
            e.preventDefault();
            clearAllFiles();
        });
    }

    // Input change event
    fileInput.addEventListener('change', function () {
        if (this.files && this.files.length > 0) {
            addFiles(this.files);
        }
    });

    // Drag and drop events
    ['dragenter', 'dragover'].forEach(function (evt) {
        uploadZone.addEventListener(evt, function (e) {
            e.preventDefault();
            e.stopPropagation();
            uploadZone.classList.add('drag-over');
        });
    });

    ['dragleave', 'drop'].forEach(function (evt) {
        uploadZone.addEventListener(evt, function (e) {
            e.preventDefault();
            e.stopPropagation();
            uploadZone.classList.remove('drag-over');
        });
    });

    uploadZone.addEventListener('drop', function (e) {
        var dt = e.dataTransfer;
        if (dt && dt.files && dt.files.length > 0) {
            addFiles(dt.files);
        }
    });

    // Keyboard activation
    uploadZone.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            fileInput.click();
        }
    });
}
