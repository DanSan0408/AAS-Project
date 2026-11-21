/*
 * Sign-in announcement pop-up. The server only renders the <dialog> when there is something to read; this opens it
 * and tells the server once it has been dismissed (button, Escape or backdrop), so it is not shown again this login.
 */
(function () {
    'use strict';

    var dialog = document.getElementById('announcementDialog');
    if (!dialog || typeof dialog.showModal !== 'function') {
        return;
    }
    var form = dialog.querySelector('form');
    var reported = false;

    function report() {
        if (reported) {
            return;
        }
        reported = true;
        try {
            fetch(form.action, {
                method: 'POST',
                body: new URLSearchParams(new FormData(form)),
                credentials: 'same-origin',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            }).catch(function () { /* worst case it shows again on the next page */ });
        } catch (e) {
            /* ignore */
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        dialog.close();
    });
    dialog.addEventListener('close', report);
    // Clicking the dimmed area outside the card dismisses it as well.
    dialog.addEventListener('click', function (event) {
        if (event.target === dialog) {
            dialog.close();
        }
    });

    function open() {
        if (!dialog.open) {
            dialog.showModal();
        }
    }
    // Wait for the page (and the loading screen) to finish so the pop-up is not hidden behind or flashed over it.
    if (document.readyState === 'complete') {
        open();
    } else {
        window.addEventListener('load', open);
    }
})();
