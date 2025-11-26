// ============== EDIT DOCUMENT PAGE JAVASCRIPT - FIXED VERSION ==============

// ============== GLOBAL VARIABLES - MUST BE DECLARED OUTSIDE DOMContentLoaded ==============
var currentStep = 1;
var formChanged = false;
var selectedTags = [];
var selectedClassifications = [];
var allExistingTags = [];
var originalFormData = {};
var uploadedFile = null;
var pdfDoc = null;
var isPasswordProtected = false;
var fileReplaced = false;
var isSubmitting = false;
var navigationConfirmed = false;
var pendingNavigationUrl = null;

// These will be set from the HTML inline script
var documentId = 0;
var originalFileName = '';
var originalPageCount = 0;
var existingTagsStr = '';
var existingClassificationsStr = '';
var publishDateStr = '';

// ============== GLOBAL FUNCTIONS - MUST BE ACCESSIBLE FROM HTML ==============

// Function called from HTML onclick
function showReplaceFileDialog() {
    console.log('Replace Document button clicked');
    // Directly open file browser instead of showing confirmation modal
    const fileInput = document.getElementById('fileInput');
    if (fileInput) {
        fileInput.click();
    } else {
        console.error('File input not found!');
    }
}

function closeReplaceFileModal() {
    document.getElementById('replaceFileModal').classList.remove('show');
}

function closePdfProtectionModal() {
    document.getElementById('pdfProtectionModal').classList.remove('show');
}

function cancelProtectedPdf() {
    const fileInput = document.getElementById('fileInput');
    fileInput.value = '';
    uploadedFile = null;
    closePdfProtectionModal();
}

function closeConfirmationModal() {
    document.getElementById('confirmationModal').classList.remove('show');
}

function changeFile() {
    const fileInput = document.getElementById('fileInput');
    fileInput.value = '';
    uploadedFile = null;
    pdfDoc = null;
    isPasswordProtected = false;
    fileReplaced = false;
    document.getElementById('fileUploadedMessage').classList.remove('show');
    document.getElementById('currentFileInfo').style.display = 'block';

    // Reset to original values
    document.getElementById('title').value = originalFormData.title;
    document.getElementById('noOfPages').value = originalFormData.noOfPages;
    document.getElementById('title').classList.remove('changed');
    document.getElementById('noOfPages').classList.remove('changed');
    document.getElementById('pagesInfoText').classList.remove('warning');
    document.getElementById('pagesInfoTextContent').innerHTML = '<i class="bi bi-info-circle"></i> Page count will be auto-updated if you replace the document';
}

function removeSelectedTag(tagName) {
    selectedTags = selectedTags.filter(t => t !== tagName);

    const checkbox = document.querySelector(`.tag-checkbox[data-tag-name="${tagName}"]`);
    if (checkbox) {
        checkbox.checked = false;
        const item = checkbox.closest('.selection-item-flex');
        if (item) item.classList.remove('selected');
    }

    renderSelectedTags();
    formChanged = true;
}

function removeSelectedClassification(classificationName) {
    selectedClassifications = selectedClassifications.filter(c => c !== classificationName);

    const checkbox = document.querySelector(`.classification-checkbox[data-classification-name="${classificationName}"]`);
    if (checkbox) {
        checkbox.checked = false;
        const item = checkbox.closest('.selection-item-flex');
        if (item) item.classList.remove('selected');
    }

    renderSelectedClassifications();
    formChanged = true;
}

function goBack() {
    if (hasFormChanged()) {
        document.getElementById('confirmationModal').classList.add('show');

        const confirmBtn = document.getElementById('confirmCancelBtn');
        confirmBtn.onclick = function() {
            navigationConfirmed = true;
            formChanged = false;
            isSubmitting = true;
            closeConfirmationModal();

            if (document.referrer && document.referrer !== window.location.href) {
                window.history.back();
            } else {
                window.location.href = '/documents';
            }
        };
    } else if (document.referrer && document.referrer !== window.location.href) {
        window.history.back();
    } else {
        window.location.href = '/documents';
    }
}

// ============== INITIALIZATION ==============
document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded, initializing...');
    console.log('Document ID from server:', documentId);
    console.log('Tags from server:', existingTagsStr);
    console.log('Classifications from server:', existingClassificationsStr);
    console.log('Publish Date from server:', publishDateStr);

    populateYearDropdown();
    initializeData();
    setupEventListeners();

    // Auto-dismiss alerts after 5 seconds
    setTimeout(() => {
        const alerts = document.querySelectorAll('.alert');
        alerts.forEach(alert => {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            if (bsAlert) bsAlert.close();
        });
    }, 5000);
});

function initializeData() {
    console.log('Initializing data...');

    // Parse publish date if exists
    if (publishDateStr && publishDateStr.trim() !== '' && publishDateStr !== 'null') {
        try {
            let dateObj;

            // Handle different date formats
            if (publishDateStr.includes('-')) {
                const parts = publishDateStr.split('-');
                if (parts.length >= 2) {
                    const year = parseInt(parts[0]);
                    const month = parseInt(parts[1]);
                    dateObj = new Date(year, month - 1, 1);
                }
            } else {
                dateObj = new Date(publishDateStr);
            }

            if (dateObj && !isNaN(dateObj.getTime())) {
                const month = String(dateObj.getMonth() + 1).padStart(2, '0');
                const year = dateObj.getFullYear();

                // Set the values after year dropdown is populated
                setTimeout(() => {
                    document.getElementById('publishMonth').value = month;
                    document.getElementById('publishYear').value = year;
                    console.log('Set publish date:', month, year);
                }, 100);
            }
        } catch (e) {
            console.error('Error parsing date:', e);
        }
    }

    // Load existing tags
    if (existingTagsStr && existingTagsStr.trim() !== '' && existingTagsStr !== 'null') {
        selectedTags = existingTagsStr.split(',')
            .map(tag => tag.trim())
            .filter(tag => tag !== '' && tag !== 'null');
        console.log('Loaded tags:', selectedTags);
    } else {
        selectedTags = [];
        console.log('No existing tags found');
    }

    // Load existing classifications
    if (existingClassificationsStr && existingClassificationsStr.trim() !== '' && existingClassificationsStr !== 'null') {
        selectedClassifications = existingClassificationsStr.split(',')
            .map(c => c.trim())
            .filter(c => c !== '' && c !== 'null');
        console.log('Loaded classifications:', selectedClassifications);
    } else {
        selectedClassifications = [];
        console.log('No existing classifications found');
    }

    // Store all existing tags for validation
    document.querySelectorAll('.tag-checkbox').forEach(checkbox => {
        const tagName = checkbox.getAttribute('data-tag-name');
        if (tagName) {
            allExistingTags.push(tagName.toLowerCase());
        }
    });

    // Attach click handlers to existing tags
    document.querySelectorAll('.selection-item-flex[data-tag-name]').forEach(item => {
        item.addEventListener('click', function() {
            const checkbox = this.querySelector('.tag-checkbox');
            const tagName = this.getAttribute('data-tag-name');

            if (checkbox.checked) {
                checkbox.checked = false;
                this.classList.remove('selected');
                selectedTags = selectedTags.filter(t => t !== tagName);
            } else {
                checkbox.checked = true;
                this.classList.add('selected');
                if (!selectedTags.includes(tagName)) {
                    selectedTags.push(tagName);
                }
            }
            renderSelectedTags();
            formChanged = true;
        });
    });

    // Attach click handlers to existing classifications
    document.querySelectorAll('.selection-item-flex[data-classification-name]').forEach(item => {
        item.addEventListener('click', function() {
            const checkbox = this.querySelector('.classification-checkbox');
            const classificationName = this.getAttribute('data-classification-name');

            if (checkbox.checked) {
                checkbox.checked = false;
                this.classList.remove('selected');
                selectedClassifications = selectedClassifications.filter(c => c !== classificationName);
            } else {
                checkbox.checked = true;
                this.classList.add('selected');
                if (!selectedClassifications.includes(classificationName)) {
                    selectedClassifications.push(classificationName);
                }
            }
            renderSelectedClassifications();
            formChanged = true;
        });
    });

    // Mark selected tags in UI
    selectedTags.forEach(tag => {
        const item = document.querySelector(`.selection-item-flex[data-tag-name="${tag}"]`);
        if (item) {
            item.classList.add('selected');
            const checkbox = item.querySelector('.tag-checkbox');
            if (checkbox) checkbox.checked = true;
        }
    });

    // Mark selected classifications in UI
    selectedClassifications.forEach(classification => {
        const item = document.querySelector(`.selection-item-flex[data-classification-name="${classification}"]`);
        if (item) {
            item.classList.add('selected');
            const checkbox = item.querySelector('.classification-checkbox');
            if (checkbox) checkbox.checked = true;
        }
    });

    renderSelectedTags();
    renderSelectedClassifications();

    // Store original form data
    captureOriginalFormData();

    console.log('Data initialization complete');
}

function setupEventListeners() {
    console.log('Setting up event listeners...');

    // File input change handler
    const fileInput = document.getElementById('fileInput');
    const dropZone = document.getElementById('dropZone');

    fileInput.addEventListener('change', (e) => {
        console.log('File input changed');
        // Hide current file info and show drop zone when file is being selected
        if (e.target.files && e.target.files.length > 0) {
            document.getElementById('currentFileInfo').style.display = 'none';
            document.getElementById('dropZone').classList.add('show');
            handleFiles(e.target.files);
        }
    });

    // Drag and drop handlers
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, e => { e.preventDefault(); e.stopPropagation(); }, false);
    });

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.add('dragover'), false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.remove('dragover'), false);
    });

    dropZone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        fileInput.files = files;
        handleFiles(files);
    });

    // PDF Protection modal buttons
    document.getElementById('forceUploadBtn').addEventListener('click', async function() {
        closePdfProtectionModal();
        isPasswordProtected = true;
        await proceedWithUpload(uploadedFile, null);
    });

    // Wizard navigation buttons
    document.getElementById('nextBtn').addEventListener('click', function() {
        console.log('Next button clicked, current step:', currentStep);

        if (currentStep === 1) {
            // Step 1 validation - no file replacement check needed, can proceed
            updateWizardStep(2);
        } else if (currentStep === 2) {
            const title = document.getElementById('title').value.trim();
            const productCode = document.getElementById('productCode').value.trim();
            const publishYear = document.getElementById('publishYear').value.trim();
            const noOfPages = document.getElementById('noOfPages').value.trim();

            if (!title || !productCode) {
                showCustomAlert('Please fill in all required fields (Title and Product Code).');
                return;
            }

            if (!publishYear) {
                showCustomAlert('Publication Year is required.');
                return;
            }

            if (!noOfPages) {
                showCustomAlert('Number of Pages is required.');
                return;
            }

            updateWizardStep(3);
        } else if (currentStep === 3) {
            updateWizardStep(4);
        }
    });

    document.getElementById('prevBtn').addEventListener('click', function() {
        console.log('Previous button clicked');
        if (currentStep > 1) {
            updateWizardStep(currentStep - 1);
        }
    });

    // Tags handling
    const newTagInput = document.getElementById('newTagInput');
    const addTagBtn = document.getElementById('addTagBtn');

    addTagBtn.addEventListener('click', addNewTag);

    newTagInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            addNewTag();
        }
    });

    newTagInput.addEventListener('input', function() {
        hideTagError();
    });

    // Cancel button handler
    document.getElementById('cancelBtn').addEventListener('click', function() {
        console.log('Cancel button clicked');
        if (hasFormChanged()) {
            showNavigationConfirmation('/documents');
        } else {
            window.location.href = '/documents';
        }
    });

    // Track form changes
    document.getElementById('editForm').addEventListener('input', function() {
        formChanged = true;
    });

    document.getElementById('editForm').addEventListener('change', function() {
        formChanged = true;
    });

    // Navigation protection
    window.history.pushState(null, '', window.location.href);

    window.addEventListener('popstate', function(e) {
        if (hasFormChanged() && !isSubmitting && !navigationConfirmed) {
            window.history.pushState(null, '', window.location.href);
            showNavigationConfirmation('back');
        }
    });

    document.addEventListener('click', function(e) {
        const link = e.target.closest('a');

        if (link && link.href && !link.hasAttribute('data-bs-toggle')) {
            const isWizardButton = link.closest('.wizard-navigation') ||
                                   link.closest('.wizard-content') ||
                                   link.id === 'confirmCancelBtn' ||
                                   link.classList.contains('modal-btn');

            const isNavLink = link.closest('.navbar') ||
                             link.closest('.sidebar') ||
                             link.closest('nav') ||
                             link.hasAttribute('href');

            if (isNavLink && !isWizardButton && hasFormChanged() && !isSubmitting && !navigationConfirmed) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                const targetUrl = link.href;
                showNavigationConfirmation(targetUrl);
                return false;
            }
        }
    }, true);

    window.addEventListener('beforeunload', function(e) {
        if (hasFormChanged() && !isSubmitting) {
            e.preventDefault();
            e.returnValue = '';
            return '';
        }
    });

    document.addEventListener('submit', function(e) {
        if (e.target.id !== 'editForm' && hasFormChanged() && !isSubmitting && !navigationConfirmed) {
            e.preventDefault();
            showNavigationConfirmation(e.target.action);
        }
    }, true);

    // Form submission
    document.getElementById('editForm').addEventListener('submit', function(e) {
        console.log('Form submitted');

        const title = document.getElementById('title').value.trim();
        const productCode = document.getElementById('productCode').value.trim();
        const publishYear = document.getElementById('publishYear').value.trim();
        const noOfPages = document.getElementById('noOfPages').value.trim();

        if (!title) {
            e.preventDefault();
            showCustomAlert('Document Title is required.');
            return false;
        }

        if (!productCode) {
            e.preventDefault();
            showCustomAlert('Product Code is required.');
            return false;
        }

        if (!publishYear) {
            e.preventDefault();
            showCustomAlert('Publication Year is required.');
            return false;
        }

        if (!noOfPages) {
            e.preventDefault();
            showCustomAlert('Number of Pages is required.');
            return false;
        }

        // Set month and year values
        document.getElementById('publishMonthField').value = document.getElementById('publishMonth').value;
        document.getElementById('publishYearField').value = document.getElementById('publishYear').value;

        const submitBtn = document.getElementById('submitBtn');
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Updating...';
        formChanged = false;
        isSubmitting = true;
        return true;
    });

    console.log('Event listeners setup complete');

    // Initialize wizard
    console.log('Initializing wizard at step 1');
    updateWizardStep(1);
}

function captureOriginalFormData() {
    originalFormData = {
        title: document.getElementById('title').value,
        productCode: document.getElementById('productCode').value,
        edition: document.getElementById('edition').value,
        publishMonth: document.getElementById('publishMonth').value,
        publishYear: document.getElementById('publishYear').value,
        noOfPages: document.getElementById('noOfPages').value,
        notes: document.getElementById('notes').value,
        tags: [...selectedTags],
        classifications: [...selectedClassifications],
        fileName: originalFileName
    };
    console.log('Captured original form data:', originalFormData);
}

function hasFormChanged() {
    const currentData = {
        title: document.getElementById('title').value,
        productCode: document.getElementById('productCode').value,
        edition: document.getElementById('edition').value,
        publishMonth: document.getElementById('publishMonth').value,
        publishYear: document.getElementById('publishYear').value,
        noOfPages: document.getElementById('noOfPages').value,
        notes: document.getElementById('notes').value,
        tags: [...selectedTags],
        classifications: [...selectedClassifications]
    };

    const changed = JSON.stringify(originalFormData) !== JSON.stringify(currentData) || fileReplaced;
    return changed;
}

// ============== POPULATE YEAR DROPDOWN ==============
function populateYearDropdown() {
    const yearSelect = document.getElementById('publishYear');
    const currentYear = new Date().getFullYear();

    for (let year = currentYear; year >= 1900; year--) {
        const option = document.createElement('option');
        option.value = year;
        option.textContent = year;
        yearSelect.appendChild(option);
    }
    console.log('Year dropdown populated');
}

// ============== FILE UPLOAD HANDLING ==============
async function handleFiles(files) {
    if (files.length > 0) {
        const file = files[0];

        if (file.type !== 'application/pdf') {
            showCustomAlert('Please upload only PDF files');
            document.getElementById('fileInput').value = '';
            return;
        }

        if (file.size > 50 * 1024 * 1024) {
            showCustomAlert('File size must be less than 50MB');
            document.getElementById('fileInput').value = '';
            return;
        }

        uploadedFile = file;
        await checkPdfProtection(file);
    }
}

async function checkPdfProtection(file) {
    try {
        const arrayBuffer = await file.arrayBuffer();
        const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
        const pdf = await loadingTask.promise;

        const restrictions = [];

        if (pdf.isEncrypted || loadingTask._transport._passwordCallback) {
            restrictions.push('Password Protected');
        }

        try {
            const permissions = await pdf.getPermissions();
            if (permissions) {
                if (permissions.includes(4) === false) {
                    restrictions.push('Print Restricted');
                }
                if (permissions.includes(16) === false) {
                    restrictions.push('Copy/Extract Restricted');
                }
                if (permissions.includes(8) === false) {
                    restrictions.push('Modification Restricted');
                }
            }
        } catch (permError) {
            console.log('Could not check permissions:', permError);
        }

        if (restrictions.length > 0) {
            showProtectionWarning(restrictions);
        } else {
            await proceedWithUpload(file, pdf);
        }
    } catch (error) {
        console.error("Error checking PDF protection:", error);
        if (error.name === 'PasswordException') {
            showProtectionWarning(['Password Protected - Cannot preview']);
        } else {
            await proceedWithUpload(file, null);
        }
    }
}

function showProtectionWarning(restrictions) {
    const protectionList = document.getElementById('protectionList');
    protectionList.innerHTML = '';

    restrictions.forEach(restriction => {
        const li = document.createElement('li');
        li.innerHTML = `<i class="bi bi-exclamation-triangle" style="color: #f59e0b; margin-right: 5px;"></i>${restriction}`;
        protectionList.appendChild(li);
    });

    document.getElementById('pdfProtectionModal').classList.add('show');
}

async function proceedWithUpload(file, pdf) {
    displayFileSuccess(file);
    await extractPdfMetadata(file, pdf);
    if (pdf) {
        pdfDoc = pdf;
    }
    fileReplaced = true;
    formChanged = true;
}

async function extractPdfMetadata(file, pdf) {
    try {
        let pageCount = 0;
        const originalPages = parseInt(originalFormData.noOfPages);

        if (pdf) {
            pageCount = pdf.numPages;
            document.getElementById('noOfPages').value = pageCount;
            document.getElementById('uploadedFilePages').textContent = pageCount + ' pages';

            // Highlight if page count changed
            if (pageCount !== originalPages) {
                document.getElementById('noOfPages').classList.add('changed');
                document.getElementById('pagesInfoText').classList.add('warning');
                document.getElementById('pagesInfoTextContent').innerHTML = `<i class="bi bi-exclamation-triangle"></i> Page count changed from ${originalPages} to ${pageCount} pages`;
            } else {
                document.getElementById('noOfPages').classList.remove('changed');
                document.getElementById('pagesInfoText').classList.remove('warning');
                document.getElementById('pagesInfoTextContent').innerHTML = '<i class="bi bi-info-circle"></i> Page count auto-detected from new PDF';
            }
        } else {
            document.getElementById('uploadedFilePages').textContent = 'Manual entry required';
            document.getElementById('pagesInfoText').classList.add('warning');
            document.getElementById('pagesInfoTextContent').innerHTML = '<i class="bi bi-exclamation-triangle"></i> Please enter the number of pages manually (password-protected PDF)';
        }

        const originalTitle = originalFormData.title;
        if (pdf) {
            const metadata = await pdf.getMetadata().catch(() => null);
            if (metadata && metadata.info && metadata.info.Title) {
                document.getElementById('title').value = metadata.info.Title;
            } else {
                const defaultTitle = file.name.replace(/\.[^/.]+$/, '');
                document.getElementById('title').value = defaultTitle;
            }
        } else {
            const defaultTitle = file.name.replace(/\.[^/.]+$/, '');
            document.getElementById('title').value = defaultTitle;
        }

        if (document.getElementById('title').value !== originalTitle) {
            document.getElementById('title').classList.add('changed');
        } else {
            document.getElementById('title').classList.remove('changed');
        }
    } catch (error) {
        console.error("Error reading PDF metadata:", error);
    }
}

function displayFileSuccess(file) {
    document.getElementById('uploadedFileName').textContent = file.name;
    document.getElementById('uploadedFileSize').textContent = formatFileSize(file.size);
    document.getElementById('dropZone').classList.remove('show');
    document.getElementById('dropZone').style.display = 'none';
    document.getElementById('fileUploadedMessage').classList.add('show');
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

// ============== WIZARD NAVIGATION ==============
function updateWizardStep(step) {
    console.log('Updating to step:', step);

    document.querySelectorAll('.wizard-step-content').forEach(content => {
        content.classList.remove('active');
    });

    const currentContent = document.querySelector(`.wizard-step-content[data-step="${step}"]`);
    if (currentContent) {
        currentContent.classList.add('active');
    }

    document.querySelectorAll('.wizard-step').forEach(stepEl => {
        const stepNum = parseInt(stepEl.getAttribute('data-step'));
        stepEl.classList.remove('active', 'completed');

        if (stepNum < step) {
            stepEl.classList.add('completed');
        } else if (stepNum === step) {
            stepEl.classList.add('active');
        }
    });

    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    const submitBtn = document.getElementById('submitBtn');

    if (step === 1) {
        prevBtn.style.display = 'none';
        nextBtn.style.display = 'inline-flex';
        submitBtn.style.display = 'none';
    } else if (step === 2 || step === 3) {
        prevBtn.style.display = 'inline-flex';
        nextBtn.style.display = 'inline-flex';
        submitBtn.style.display = 'none';
    } else if (step === 4) {
        prevBtn.style.display = 'inline-flex';
        nextBtn.style.display = 'none';
        submitBtn.style.display = 'inline-flex';
        updateReviewSection();
    }

    currentStep = step;
}

// ============== TAGS HANDLING ==============
function addNewTag() {
    let tagName = document.getElementById('newTagInput').value.trim();

    if (!tagName) {
        showTagError('<i class="bi bi-exclamation-circle"></i> Please enter a tag name');
        return;
    }

    if (tagName.includes(' ')) {
        showTagError('<i class="bi bi-exclamation-circle"></i> Spaces are not allowed in tag names');
        return;
    }

    tagName = tagName.toLowerCase();

    if (allExistingTags.includes(tagName)) {
        const existingItem = Array.from(document.querySelectorAll('.selection-item-flex[data-tag-name]')).find(
            item => item.getAttribute('data-tag-name').toLowerCase() === tagName
        );

        if (existingItem) {
            blinkItem(existingItem);
            showTagError('<i class="bi bi-info-circle"></i> Tag already exists in available tags');

            const checkbox = existingItem.querySelector('.tag-checkbox');
            if (checkbox && !checkbox.checked) {
                checkbox.checked = true;
                if (!selectedTags.includes(tagName)) {
                    selectedTags.push(tagName);
                    existingItem.classList.add('selected');
                    renderSelectedTags();
                    formChanged = true;
                }
            }
        }
        document.getElementById('newTagInput').value = '';
        return;
    }

    if (selectedTags.some(t => t.toLowerCase() === tagName)) {
        showTagError('<i class="bi bi-info-circle"></i> Tag is already selected');
        document.getElementById('newTagInput').value = '';
        return;
    }

    selectedTags.push(tagName);
    allExistingTags.push(tagName);

    const tagsArea = document.getElementById('tagsSelectionArea');
    const emptyState = tagsArea.querySelector('.empty-state');
    if (emptyState) {
        emptyState.remove();
    }

    const newTagItem = createNewSelectionItem(tagName, 'tag');
    newTagItem.classList.add('selected');
    const checkbox = newTagItem.querySelector('.tag-checkbox');
    checkbox.checked = true;

    tagsArea.appendChild(newTagItem);
    renderSelectedTags();
    document.getElementById('newTagInput').value = '';
    formChanged = true;
}

function createNewSelectionItem(name, type) {
    const item = document.createElement('div');
    item.className = 'selection-item-flex';
    item.setAttribute(`data-${type}-name`, name);
    item.innerHTML = `
        <input type="checkbox" class="selection-checkbox ${type}-checkbox" data-${type}-name="${name}" style="display: none;">
        <span class="selection-label">${name}</span>
    `;

    item.addEventListener('click', function() {
        const checkbox = this.querySelector(`.${type}-checkbox`);
        const itemName = this.getAttribute(`data-${type}-name`);

        if (checkbox.checked) {
            checkbox.checked = false;
            this.classList.remove('selected');
            if (type === 'tag') {
                selectedTags = selectedTags.filter(t => t !== itemName);
                renderSelectedTags();
            } else {
                selectedClassifications = selectedClassifications.filter(c => c !== itemName);
                renderSelectedClassifications();
            }
        } else {
            checkbox.checked = true;
            this.classList.add('selected');
            if (type === 'tag') {
                if (!selectedTags.includes(itemName)) {
                    selectedTags.push(itemName);
                }
                renderSelectedTags();
            } else {
                if (!selectedClassifications.includes(itemName)) {
                    selectedClassifications.push(itemName);
                }
                renderSelectedClassifications();
            }
        }
        formChanged = true;
    });

    return item;
}

function showTagError(message) {
    const tagErrorMessage = document.getElementById('tagErrorMessage');
    tagErrorMessage.innerHTML = message;
    tagErrorMessage.classList.add('show');
    setTimeout(() => {
        hideTagError();
    }, 3000);
}

function hideTagError() {
    const tagErrorMessage = document.getElementById('tagErrorMessage');
    tagErrorMessage.classList.remove('show');
}

function renderSelectedTags() {
    const container = document.getElementById('selectedTagsContainer');
    container.innerHTML = '';

    if (selectedTags.length === 0) {
        container.innerHTML = '<div class="empty-state">No tags selected</div>';
    } else {
        selectedTags.forEach(tag => {
            const chip = document.createElement('span');
            const isNewTag = !originalFormData.tags.includes(tag);
            chip.className = 'selected-chip' + (isNewTag ? ' new-item' : '');
            chip.innerHTML = `
                ${tag}
                <span class="chip-remove" onclick="removeSelectedTag('${tag}')">
                    <i class="bi bi-x"></i>
                </span>
            `;
            container.appendChild(chip);
        });
    }

    document.getElementById('tagNamesField').value = selectedTags.join(',');
}

// ============== CLASSIFICATIONS HANDLING ==============
function renderSelectedClassifications() {
    const container = document.getElementById('selectedClassificationsContainer');
    container.innerHTML = '';

    if (selectedClassifications.length === 0) {
        container.innerHTML = '<div class="empty-state">No classifications selected</div>';
    } else {
        selectedClassifications.forEach(classification => {
            const chip = document.createElement('span');
            const isNewClassification = !originalFormData.classifications.includes(classification);
            chip.className = 'selected-chip classification-chip' + (isNewClassification ? ' new-item' : '');
            chip.innerHTML = `
                ${classification}
                <span class="chip-remove" onclick="removeSelectedClassification('${classification}')">
                    <i class="bi bi-x"></i>
                </span>
            `;
            container.appendChild(chip);
        });
    }

    document.getElementById('classificationNamesField').value = selectedClassifications.join(',');
}

// ============== BLINK ANIMATION ==============
function blinkItem(element) {
    element.classList.add('blink');
    setTimeout(() => {
        element.classList.remove('blink');
    }, 1800);
}

// ============== REVIEW SECTION ==============
async function updateReviewSection() {
    console.log('Updating review section');

    function formatPublishDate(month, year) {
        if (!year) return '-';
        if (month) {
            const monthNames = ['', 'January', 'February', 'March', 'April', 'May', 'June',
                              'July', 'August', 'September', 'October', 'November', 'December'];
            return `${monthNames[parseInt(month)]} ${year}`;
        }
        return year;
    }

    // File Information
    if (fileReplaced && uploadedFile) {
        document.getElementById('reviewFileName').innerHTML = `<span class="value-changed">${uploadedFile.name}</span>`;
        document.getElementById('reviewFileSize').textContent = formatFileSize(uploadedFile.size);
    } else {
        document.getElementById('reviewFileName').textContent = originalFileName || '-';
        document.getElementById('reviewFileSize').textContent = '-';
    }

    const currentPages = document.getElementById('noOfPages').value;
    if (currentPages !== originalFormData.noOfPages) {
        document.getElementById('reviewFilePages').innerHTML = `<span class="value-changed">${currentPages}</span>`;
    } else {
        document.getElementById('reviewFilePages').textContent = currentPages || '-';
    }

    const currentTitle = document.getElementById('title').value || '-';
    const currentProductCode = document.getElementById('productCode').value || '-';
    const currentEdition = document.getElementById('edition').value || '-';
    const currentMonth = document.getElementById('publishMonth').value;
    const currentYear = document.getElementById('publishYear').value;
    const currentNotes = document.getElementById('notes').value;

    document.getElementById('reviewTitle').innerHTML = currentTitle !== originalFormData.title ?
        `<span class="value-changed">${currentTitle}</span>` : currentTitle;

    document.getElementById('reviewProductCode').innerHTML = currentProductCode !== originalFormData.productCode ?
        `<span class="value-changed">${currentProductCode}</span>` : currentProductCode;

    document.getElementById('reviewEdition').innerHTML = currentEdition !== originalFormData.edition ?
        `<span class="value-changed">${currentEdition}</span>` : currentEdition;

    const currentPublishDate = formatPublishDate(currentMonth, currentYear);
    const originalPublishDate = formatPublishDate(originalFormData.publishMonth, originalFormData.publishYear);
    document.getElementById('reviewPublishDate').innerHTML = currentPublishDate !== originalPublishDate ?
        `<span class="value-changed">${currentPublishDate}</span>` : currentPublishDate;

    const displayNotes = currentNotes ? (currentNotes.length > 100 ? currentNotes.substring(0, 100) + '...' : currentNotes) : '-';
    document.getElementById('reviewNotes').innerHTML = currentNotes !== originalFormData.notes ?
        `<span class="value-changed">${displayNotes}</span>` : displayNotes;

    // Tags with highlighting
    const reviewTagsContainer = document.getElementById('reviewTags');
    reviewTagsContainer.innerHTML = '';
    if (selectedTags.length === 0) {
        reviewTagsContainer.innerHTML = '<span style="color: #9ca3af; font-size: 14px;">No tags selected</span>';
    } else {
        selectedTags.forEach(tag => {
            const chip = document.createElement('span');
            const isNewTag = !originalFormData.tags.includes(tag);
            chip.className = 'selected-chip' + (isNewTag ? ' new-item' : '');
            chip.textContent = tag;
            reviewTagsContainer.appendChild(chip);
        });
    }

    const reviewClassificationsContainer = document.getElementById('reviewClassifications');
    reviewClassificationsContainer.innerHTML = '';
    if (selectedClassifications.length === 0) {
        reviewClassificationsContainer.innerHTML = '<span style="color: #9ca3af; font-size: 14px;">No classifications selected</span>';
    } else {
        selectedClassifications.forEach(classification => {
            const chip = document.createElement('span');
            const isNewClassification = !originalFormData.classifications.includes(classification);
            chip.className = 'selected-chip classification-chip' + (isNewClassification ? ' new-item' : '');
            chip.textContent = classification;
            reviewClassificationsContainer.appendChild(chip);
        });
    }

    if (fileReplaced && pdfDoc && !isPasswordProtected) {
        await renderReviewPdfPreview(pdfDoc);
    } else if (!fileReplaced && documentId) {
        await loadOriginalPdfPreview();
    } else {
        const canvas = document.getElementById('reviewPdfCanvas');
        const ctx = canvas.getContext('2d');
        canvas.width = 400;
        canvas.height = 500;
        ctx.fillStyle = '#f3f4f6';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = '#6b7280';
        ctx.font = '16px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('Preview not available', canvas.width / 2, canvas.height / 2 - 10);
        if (isPasswordProtected) {
            ctx.fillText('(Password Protected)', canvas.width / 2, canvas.height / 2 + 15);
        }
    }
}

async function renderReviewPdfPreview(pdf) {
    try {
        const page = await pdf.getPage(1);
        const canvas = document.getElementById('reviewPdfCanvas');
        const context = canvas.getContext('2d');

        const viewport = page.getViewport({ scale: 1.2 });
        canvas.height = viewport.height;
        canvas.width = viewport.width;

        const renderContext = {
            canvasContext: context,
            viewport: viewport
        };

        await page.render(renderContext).promise;
        console.log('New PDF preview rendered successfully');
    } catch (error) {
        console.error("Error rendering PDF preview:", error);
    }
}

async function loadOriginalPdfPreview() {
    console.log('Loading original PDF preview for document ID:', documentId);

    if (!documentId) {
        console.error('No document ID available');
        return;
    }

    const pdfUrl = `/document/view/${documentId}`;
    const canvas = document.getElementById('reviewPdfCanvas');

    try {
        console.log('Fetching PDF from:', pdfUrl);
        const loadingTask = pdfjsLib.getDocument(pdfUrl);
        const pdf = await loadingTask.promise;

        console.log('PDF loaded, rendering first page');
        const page = await pdf.getPage(1);
        const context = canvas.getContext('2d');

        const viewport = page.getViewport({ scale: 1.2 });
        canvas.height = viewport.height;
        canvas.width = viewport.width;

        const renderContext = {
            canvasContext: context,
            viewport: viewport
        };

        await page.render(renderContext).promise;
        console.log('Original PDF preview rendered successfully');
    } catch (error) {
        console.error("Error loading PDF preview:", error);
        const ctx = canvas.getContext('2d');
        canvas.width = 400;
        canvas.height = 500;
        ctx.fillStyle = '#f3f4f6';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = '#6b7280';
        ctx.font = '16px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('Preview not available', canvas.width / 2, canvas.height / 2 - 10);
        ctx.fillText('(Document may be protected)', canvas.width / 2, canvas.height / 2 + 15);
    }
}

// ============== NAVIGATION PROTECTION ==============
function showNavigationConfirmation(targetUrl) {
    console.log('Showing navigation confirmation for:', targetUrl);
    pendingNavigationUrl = targetUrl;
    const modal = document.getElementById('confirmationModal');
    modal.classList.add('show');

    const confirmBtn = document.getElementById('confirmCancelBtn');
    const newConfirmBtn = confirmBtn.cloneNode(true);
    confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

    newConfirmBtn.onclick = function() {
        console.log('Navigation confirmed');
        navigationConfirmed = true;
        formChanged = false;
        isSubmitting = true;
        closeConfirmationModal();

        if (pendingNavigationUrl === 'back') {
            window.history.back();
        } else {
            window.location.href = pendingNavigationUrl;
        }
    };
}

function showCustomAlert(message) {
    const alertDiv = document.createElement('div');
    alertDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: #ef4444;
        color: white;
        padding: 16px 24px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
        z-index: 10000;
        animation: slideInRight 0.3s ease;
        max-width: 400px;
        display: flex;
        align-items: center;
        gap: 10px;
    `;
    alertDiv.innerHTML = `
        <i class="bi bi-exclamation-circle-fill" style="font-size: 20px;"></i>
        <span>${message}</span>
    `;
    document.body.appendChild(alertDiv);

    setTimeout(() => {
        alertDiv.style.animation = 'slideOutRight 0.3s ease';
        setTimeout(() => alertDiv.remove(), 300);
    }, 3000);
}