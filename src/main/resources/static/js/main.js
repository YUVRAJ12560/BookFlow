/* ==========================================
   BookFlow - Main JavaScript
   ========================================== */

// Bootstrap is loaded via CDN in the base template.
// Place shared client-side behaviour here.

document.addEventListener('DOMContentLoaded', function () {
    // Auto-dismiss alert messages after 4 seconds
    const alerts = document.querySelectorAll('.alert.auto-dismiss');
    alerts.forEach(function (alert) {
        setTimeout(function () {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            bsAlert.close();
        }, 4000);
    });
});
