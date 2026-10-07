/* ==========================================
   BookFlow — Main JavaScript (v2)
   ========================================== */

document.addEventListener('DOMContentLoaded', function () {

    // ── Auto-dismiss alert messages ─────────────────────────────
    document.querySelectorAll('.alert.auto-dismiss').forEach(function (alert) {
        setTimeout(function () {
            var bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            bsAlert.close();
        }, 4500);
    });

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

    // ── Upload zone drag-and-drop highlight ─────────────────────
    var uploadZone = document.querySelector('.bf-upload-zone');
    if (uploadZone) {
        ['dragenter', 'dragover'].forEach(function (evt) {
            uploadZone.addEventListener(evt, function (e) {
                e.preventDefault();
                uploadZone.classList.add('drag-over');
            });
        });
        ['dragleave', 'drop'].forEach(function (evt) {
            uploadZone.addEventListener(evt, function (e) {
                e.preventDefault();
                uploadZone.classList.remove('drag-over');
            });
        });
        uploadZone.addEventListener('drop', function (e) {
            var files = e.dataTransfer && e.dataTransfer.files;
            if (files && files.length > 0) {
                var fileInput = uploadZone.querySelector('input[type="file"]');
                if (fileInput) {
                    // Use DataTransfer to assign dropped files
                    try {
                        var dt = new DataTransfer();
                        dt.items.add(files[0]);
                        fileInput.files = dt.files;
                        updateFileLabel(fileInput);
                    } catch (err) { /* Safari fallback — user must click */ }
                }
            }
        });
    }

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

});

// File label update — used by upload form (inline onchange + drag-drop)
function updateLabel(input) {
    updateFileLabel(input);
}
function updateFileLabel(input) {
    var lbl = document.getElementById('file-label');
    if (!lbl) return;
    if (input.files && input.files[0]) {
        lbl.textContent = '✓ Selected: ' + input.files[0].name;
        lbl.style.color = '#15803d';
    }
}
