// API Base URL
const API_BASE_URL = '/api';

// Check license status when page loads
window.addEventListener('DOMContentLoaded', () => {
    checkLicenseStatus();
});

/**
 * Check license status and redirect if needed
 */
async function checkLicenseStatus() {
    try {
        const response = await fetch(`${API_BASE_URL}/license/status`);
        const data = await response.json();

        // Get current page
        const currentPage = window.location.pathname;

        // Don't check license on activation page itself
        if (currentPage.includes('license-activation')) {
            return;
        }

        if (!data.isActive || !data.isValid) {
            // License not active or expired
            if (!currentPage.includes('license-activation')) {
                showLicenseAlert(data);
                setTimeout(() => {
                    window.location.href = '/license-activation';
                }, 2000);
            }
            return;
        }

        // Store license info in sessionStorage
        sessionStorage.setItem('licenseValid', 'true');
        sessionStorage.setItem('edition', data.edition);
        sessionStorage.setItem('bulkUploadAllowed', data.bulkUploadAllowed);
        sessionStorage.setItem('expiryDate', data.expiryDate);
        sessionStorage.setItem('daysRemaining', data.daysRemaining);

        // Show warning if license expires soon (within 30 days)
        if (data.daysRemaining <= 30 && data.daysRemaining > 0) {
            showExpiryWarning(data.daysRemaining);
        }

        console.log('✓ License valid - Edition:', data.edition);

    } catch (error) {
        console.error('License check failed:', error);
    }
}

/**
 * Show license alert message
 */
function showLicenseAlert(data) {
    let message = '';
    if (!data.isActive) {
        message = '⚠️ License Not Activated\n\nPlease activate your license to continue.\n\nRedirecting to activation page...';
    } else if (!data.isValid) {
        message = '⚠️ License Expired\n\nYour license has expired.\n\nRedirecting to activation page...';
    }

    const overlay = document.createElement('div');
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.8);
        z-index: 99999;
        display: flex;
        align-items: center;
        justify-content: center;
    `;

    const alertBox = document.createElement('div');
    alertBox.style.cssText = `
        background: white;
        padding: 30px;
        border-radius: 12px;
        max-width: 400px;
        text-align: center;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
    `;
    alertBox.innerHTML = `
        <div style="font-size: 48px; margin-bottom: 15px;">🔒</div>
        <h2 style="margin-bottom: 15px; color: #e53e3e;">License Required</h2>
        <p style="color: #4a5568; line-height: 1.6; white-space: pre-line;">${message}</p>
    `;

    overlay.appendChild(alertBox);
    document.body.appendChild(overlay);
}

/**
 * Show expiry warning banner
 */
function showExpiryWarning(daysRemaining) {
    const warningDiv = document.createElement('div');
    warningDiv.id = 'license-expiry-warning';
    warningDiv.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        background: #fff3cd;
        color: #856404;
        padding: 12px 20px;
        text-align: center;
        border-bottom: 2px solid #ffc107;
        z-index: 9999;
        font-weight: 600;
        font-size: 14px;
    `;
    warningDiv.innerHTML = `
        ⚠️ License Expiring Soon: ${daysRemaining} days remaining. Please contact your administrator for renewal.
    `;
    document.body.prepend(warningDiv);
}