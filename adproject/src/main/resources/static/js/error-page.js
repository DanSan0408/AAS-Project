(function () {
    'use strict';
    document.addEventListener('click', function (event) {
        var target = event.target.closest('[data-error-action]');
        if (!target) {
            return;
        }
        var action = target.getAttribute('data-error-action');
        if (action === 'reload') {
            window.location.reload();
        } else if (action === 'back') {
            if (window.history.length > 1) {
                window.history.back();
            } else {
                window.location.href = '/';
            }
        } else if (action === 'copy') {
            var reference = document.getElementById('error-reference');
            if (reference && navigator.clipboard) {
                navigator.clipboard.writeText(reference.textContent.trim()).then(function () {
                    target.textContent = 'Copied';
                });
            }
        }
    });
}());
