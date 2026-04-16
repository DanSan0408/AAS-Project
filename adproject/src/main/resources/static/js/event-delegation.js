/**
 * adproject - Global Event Delegation Script
 *
 * This script replaces all inline 'onclick', 'onsubmit', etc., event handlers
 * with a centralized, data-attribute-driven system to comply with Content Security Policy.
 */
(function() {
    'use strict';

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
