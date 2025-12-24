// =====================================================
// License Activation JavaScript
// =====================================================

let selectedFile = null;

// Load system info when page loads
window.addEventListener('DOMContentLoaded', async function() {
    await loadSystemInfo();
});

/**
 * Load system information from backend
 */
async function loadSystemInfo() {
    try {
        const response = await fetch('/api/license/system-info');

        if (!response.ok) {
            throw new Error('Failed to load system information');
        }

        const data = await response.json();

        document.getElementById('systemName').value = data.systemName || 'Unknown';
        document.getElementById('requestCode').value = data.requestCode || 'Error generating code';

    } catch (error) {
        console.error('Error loading system info:', error);
        showStatus('error', 'Failed to load system information. Please refresh the page.');
    }
}

/**
 * Handle file selection
 */
function handleFileSelect(event) {
    const file = event.target.files[0];

    if (!file) {
        return;
    }

    // Validate file extension
    if (!file.name.endsWith('.lic')) {
        showStatus('error', 'Please select a valid license file (.lic)');
        event.target.value = '';
        return;
    }

    selectedFile = file;
    document.getElementById('fileName').textContent = file.name;
    document.getElementById('activateBtn').disabled = false;

    showStatus('info', 'License file selected. Click "Activate License" to continue.');
}

/**
 * Copy request code to clipboard
 */
function copyRequestCode() {
    const requestCode = document.getElementById('requestCode').value;

    // Copy to clipboard
    navigator.clipboard.writeText(requestCode).then(() => {
        showStatus('success', 'Request code copied to clipboard!');

        // Reset message after 2 seconds
        setTimeout(() => {
            hideStatus();
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy:', err);
        showStatus('error', 'Failed to copy request code');
    });
}

/**
 * Activate license
 */
async function activateLicense() {
    if (!selectedFile) {
        showStatus('error', 'Please select a license file first');
        return;
    }

    const activateBtn = document.getElementById('activateBtn');
    const originalText = activateBtn.textContent;

    try {
        // Show loading state
        activateBtn.disabled = true;
        activateBtn.textContent = 'Activating...';
        showStatus('info', 'Activating license. Please wait...');

        // Create form data
        const formData = new FormData();
        formData.append('file', selectedFile);

        // Send activation request
        const response = await fetch('/api/license/activate', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (response.ok) {
            // Success
            showStatus('success', data.message || 'License activated successfully!');

            // Show edition info
            if (data.edition) {
                const editionInfo = data.edition === 'ED2'
                    ? 'ED2 Professional Edition (Bulk Upload Enabled)'
                    : 'ED1 Standard Edition';

                setTimeout(() => {
                    showStatus('success', `License activated: ${editionInfo}`);
                }, 2000);
            }

            // Redirect to home page after 3 seconds
            setTimeout(() => {
                window.location.href = '/documents';
            }, 3000);

        } else {
            // Error
            throw new Error(data.error || 'Activation failed');
        }

    } catch (error) {
        console.error('Activation error:', error);
        showStatus('error', error.message || 'Failed to activate license. Please check the file and try again.');
        activateBtn.disabled = false;
        activateBtn.textContent = originalText;
    }
}

/**
 * Cancel activation and go back
 */
function cancelActivation() {
    // Check if we came from another page
    if (document.referrer && document.referrer !== window.location.href) {
        window.history.back();
    } else {
        window.location.href = '/documents';
    }
}

/**
 * Show status message
 */
function showStatus(type, message) {
    const statusDiv = document.getElementById('statusMessage');
    statusDiv.className = `status-message ${type}`;
    statusDiv.textContent = message;
}

/**
 * Hide status message
 */
function hideStatus() {
    const statusDiv = document.getElementById('statusMessage');
    statusDiv.className = 'status-message';
    statusDiv.style.display = 'none';
}