/**
 * Show/hide button for password fields. Every <input type="password"> on the page gets one, so a new form needs nothing
 * except loading this script. Opt a field out with data-password-toggle="off".
 *
 * The password goes back to hidden when the form is submitted and when the page is left, so a page restored with the
 * back button never shows it.
 */
(function () {
    'use strict';

    var EYE = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" ' +
        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' +
        '<path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>';
    var EYE_OFF = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" ' +
        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' +
        '<path d="M17.94 17.94A10.9 10.9 0 0 1 12 19c-7 0-11-7-11-7a19.8 19.8 0 0 1 5.06-5.94"/>' +
        '<path d="M9.9 4.24A10.1 10.1 0 0 1 12 4c7 0 11 7 11 7a19.9 19.9 0 0 1-3.17 4.19"/>' +
        '<path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

    var revealed = [];

    function hide(input, button) {
        input.type = 'password';
        button.setAttribute('aria-label', 'Show password');
        button.setAttribute('aria-pressed', 'false');
        button.innerHTML = EYE;
    }

    function enhance(input) {
        if (!input.parentNode || (input.dataset && input.dataset.passwordToggle === 'off')) {
            return;
        }
        if (input.parentNode.className && String(input.parentNode.className).indexOf('password-field') !== -1) {
            return; // already done
        }
        var wrapper = document.createElement('div');
        wrapper.className = 'password-field';
        input.parentNode.insertBefore(wrapper, input);
        wrapper.appendChild(input);

        var button = document.createElement('button');
        button.type = 'button'; // never submits the form
        button.className = 'password-toggle';
        hide(input, button);
        wrapper.appendChild(button);

        button.addEventListener('click', function () {
            if (input.type === 'password') {
                input.type = 'text';
                button.setAttribute('aria-label', 'Hide password');
                button.setAttribute('aria-pressed', 'true');
                button.innerHTML = EYE_OFF;
            } else {
                hide(input, button);
            }
        });

        var reset = function () { hide(input, button); };
        revealed.push(reset);
        var form = input.closest ? input.closest('form') : null;
        if (form) {
            form.addEventListener('submit', reset);
        }
    }

    function init() {
        var inputs = document.querySelectorAll('input[type="password"]');
        for (var i = 0; i < inputs.length; i++) {
            enhance(inputs[i]);
        }
    }

    window.addEventListener('pagehide', function () {
        revealed.forEach(function (reset) { reset(); });
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
