/**
 * adproject - Global Event Delegation Script
 *
 * This script replaces all inline 'onclick', 'onsubmit', etc., event handlers
 * with a centralized, data-attribute-driven system to comply with Content Security Policy.
 */
(function() {
    'use strict';

    function createBulkRow(type, email, username) {
        const row = document.createElement('tr');

        const emailCell = document.createElement('td');
        emailCell.style.padding = '8px';
        emailCell.style.borderBottom = '1px solid #eee';

        const emailInput = document.createElement('input');
        emailInput.type = 'email';
        emailInput.name = 'emails';
        emailInput.className = 'bulk-email';
        emailInput.placeholder = type + '@example.com';
        emailInput.value = email || '';
        emailInput.style.width = '100%';
        emailInput.style.padding = '8px';
        emailInput.style.border = '1px solid #ddd';
        emailInput.style.borderRadius = '6px';
        emailCell.appendChild(emailInput);

        const usernameCell = document.createElement('td');
        usernameCell.style.padding = '8px';
        usernameCell.style.borderBottom = '1px solid #eee';

        const usernameInput = document.createElement('input');
        usernameInput.type = 'text';
        usernameInput.name = 'usernames';
        usernameInput.className = 'bulk-username';
        usernameInput.placeholder = 'Optional';
        usernameInput.value = username || '';
        usernameInput.style.width = '100%';
        usernameInput.style.padding = '8px';
        usernameInput.style.border = '1px solid #ddd';
        usernameInput.style.borderRadius = '6px';
        usernameCell.appendChild(usernameInput);

        const actionCell = document.createElement('td');
        actionCell.style.padding = '8px';
        actionCell.style.borderBottom = '1px solid #eee';
        actionCell.style.textAlign = 'center';

        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.className = 'action-button delete remove-bulk-row';
        removeButton.style.padding = '6px 10px';
        removeButton.textContent = 'Remove';
        actionCell.appendChild(removeButton);

        row.appendChild(emailCell);
        row.appendChild(usernameCell);
        row.appendChild(actionCell);
        return row;
    }

    function initBulkTable(config) {
        const form = document.getElementById(config.formId);
        const tbody = document.getElementById(config.tbodyId);
        const addBtn = document.getElementById(config.addButtonId);
        if (!form || !tbody || !addBtn) {
            return;
        }

        function addRow(email, username) {
            tbody.appendChild(createBulkRow(config.typeLabel, email, username));
        }

        if (!tbody.querySelector('tr')) {
            addRow('', '');
        }

        addBtn.addEventListener('click', function() {
            addRow('', '');
        });

        tbody.addEventListener('click', function(event) {
            const button = event.target.closest('.remove-bulk-row');
            if (!button) {
                return;
            }
            const rows = tbody.querySelectorAll('tr');
            if (rows.length <= 1) {
                const emailInput = rows[0].querySelector('.bulk-email');
                const usernameInput = rows[0].querySelector('.bulk-username');
                if (emailInput) emailInput.value = '';
                if (usernameInput) usernameInput.value = '';
                return;
            }
            const row = button.closest('tr');
            if (row) {
                row.remove();
            }
        });

        tbody.addEventListener('paste', function(event) {
            const target = event.target;
            if (!target || !target.classList || (!target.classList.contains('bulk-email') && !target.classList.contains('bulk-username'))) {
                return;
            }

            const clipboard = event.clipboardData || window.clipboardData;
            if (!clipboard) {
                return;
            }

            const text = clipboard.getData('text');
            if (!text || (text.indexOf('\t') === -1 && text.indexOf('\n') === -1 && text.indexOf('\r') === -1)) {
                return;
            }

            event.preventDefault();

            const row = target.closest('tr');
            const existingRows = Array.from(tbody.querySelectorAll('tr'));
            const startIndex = Math.max(existingRows.indexOf(row), 0);
            const lines = text.replace(/\r/g, '\n').split('\n').filter(function(line) {
                return line.trim() !== '';
            });

            while (tbody.querySelectorAll('tr').length < startIndex + lines.length) {
                addRow('', '');
            }

            lines.forEach(function(line, offset) {
                const columns = line.split('\t');
                const targetRow = tbody.querySelectorAll('tr')[startIndex + offset];
                const emailInput = targetRow.querySelector('.bulk-email');
                const usernameInput = targetRow.querySelector('.bulk-username');
                if (emailInput) {
                    emailInput.value = (columns[0] || '').trim();
                }
                if (usernameInput) {
                    usernameInput.value = (columns[1] || '').trim();
                }
            });
        });

        form.addEventListener('submit', function(event) {
            const emailInputs = Array.from(tbody.querySelectorAll('.bulk-email'));
            const hasAnyEmail = emailInputs.some(function(input) {
                return input.value.trim() !== '';
            });
            if (!hasAnyEmail) {
                event.preventDefault();
                alert('Please provide at least one email address.');
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function() {
        initBulkTable({
            formId: 'bulkStudentsForm',
            tbodyId: 'bulkStudentsRows',
            addButtonId: 'addStudentRowBtn',
            typeLabel: 'student'
        });

        initBulkTable({
            formId: 'bulkLecturersForm',
            tbodyId: 'bulkLecturersRows',
            addButtonId: 'addLecturerRowBtn',
            typeLabel: 'lecturer'
        });
    });

    // =========================================================================
    // == GENERIC EVENT HANDLERS (Can be used on any page)
    // =========================================================================

    /**
     * --- Confirmation Dialogs ---
     * Handles any form with data-action="confirm-submit".
     * Reads the confirmation message from data-confirm-message.
     * Usage: <form data-action="confirm-submit" data-confirm-message="Are you sure?">
     */
    document.addEventListener('submit', event => {
        const form = event.target.closest('form[data-action="confirm-submit"]');
        if (form) {
            const message = form.dataset.confirmMessage || 'Are you sure you want to proceed?';
            if (!confirm(message)) {
                event.preventDefault(); // Stop submission if user cancels
            }
        }
    });

    /**
     * --- Prevent Double Submissions ---
     * Disables submit buttons after form submission to prevent accidental double-submissions.
     * Usage: Add to any form that processes data on the server.
     * Works with forms that have data-action="prevent-double-submit" or all forms by default
     */
    document.addEventListener('submit', event => {
        const form = event.target.closest('form');
        if (form && !event.defaultPrevented) {
            // Find all submit buttons and disable them
            const submitButtons = form.querySelectorAll('button[type="submit"]');
            submitButtons.forEach(button => {
                button.disabled = true;
                button.classList.add('disabled');
                // Optionally change the button text to indicate processing
                if (button.dataset.loadingText) {
                    button.dataset.originalText = button.textContent;
                    button.textContent = button.dataset.loadingText;
                }
            });
        }
    });

    /**
     * Handles any link with data-action="confirm-link".
     * Usage: <a href="..." data-action="confirm-link" data-confirm-message="Are you sure?">
     */
    document.addEventListener('click', event => {
        const link = event.target.closest('a[data-action="confirm-link"]');
        if (link) {
            const message = link.dataset.confirmMessage || 'Are you sure?';
            if (!confirm(message)) {
                event.preventDefault(); // Stop navigation
            }
        }
    });

    /**
     * --- UI Toggles & Actions ---
     * Handles generic dropdowns.
     * Usage: <button data-action="toggle-dropdown" data-target-id="my-dropdown">
     *        <div id="my-dropdown" class="dropdown-menu">...</div>
     */
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-action="toggle-dropdown"]');
        if (button) {
            event.stopPropagation(); // Prevent the document click listener from closing it immediately
            const targetId = button.dataset.targetId;
            const dropdown = document.getElementById(targetId);
            if (dropdown) {
                const isVisible = dropdown.classList.contains('show');
                // Hide all other dropdowns first
                document.querySelectorAll('.dropdown-menu.show').forEach(d => {
                    if (d.id !== targetId) {
                        d.classList.remove('show');
                    }
                });
                // Toggle the target one
                dropdown.classList.toggle('show');
            }
        } else if (!event.target.closest('.dropdown-menu')) {
            // Click was outside any dropdown menu, so hide all
            document.querySelectorAll('.dropdown-menu.show').forEach(d => d.classList.remove('show'));
        }
    });

    /**
     * Handles "Select All" checkboxes.
     * Usage: <input type="checkbox" data-action="toggle-select-all" data-target-selector=".user-checkbox">
     */
    document.addEventListener('change', event => {
        const source = event.target.closest('input[type="checkbox"][data-action="toggle-select-all"]');
        if (source) {
            const targetSelector = source.dataset.targetSelector;
            if (targetSelector) {
                document.querySelectorAll(targetSelector).forEach(checkbox => {
                    checkbox.checked = source.checked;
                });
            }
        }
    });
    
    /**
     * Handles printing the page.
     * Usage: <button data-action="print-page">
     */
    document.addEventListener('click', event => {
        if (event.target.closest('[data-action="print-page"]')) {
            window.print();
        }
    });

    /**
     * --- Form-related Actions ---
     * Auto-submits a form when an element inside it changes.
     * Usage: <select data-action="auto-submit-form">
     */
    document.addEventListener('change', event => {
        const element = event.target.closest('[data-action="auto-submit-form"]');
        if (element) {
            const form = element.closest('form');
            if (form) {
                form.submit();
            }
        }
    });

    /**
     * Handles history back.
     * Usage: <button data-action="history-back">
     */
    document.addEventListener('click', event => {
        if (event.target.closest('[data-action="history-back"]')) {
            window.history.back();
        }
    });

    /**
     * Handles page reload.
     * Usage: <button data-action="reload-page">
     */
    document.addEventListener('click', event => {
        if (event.target.closest('[data-action="reload-page"]')) {
            window.location.reload();
        }
    });

})();
