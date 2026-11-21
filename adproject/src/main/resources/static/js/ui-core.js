/*
 * Route progress: a thin bar across the top while the next page loads, so a click on a link or a form
 * submit gives immediate feedback instead of a frozen page. Pure transform animation (see css/ui-core.css).
 * It ignores new-tab links, downloads, other sites and in-page anchors, and clears itself when the page is shown
 * again from the back/forward cache or after a safety timeout (a cancelled navigation must not leave it running).
 */
(function () {
    'use strict';

    var SAFETY_MS = 12000;
    var bar = null;
    var safety = null;

    function ensureBar() {
        if (!bar) {
            bar = document.createElement('div');
            bar.className = 'ui-route-progress';
            bar.setAttribute('aria-hidden', 'true');
            document.body.appendChild(bar);
        }
        return bar;
    }

    function start() {
        var b = ensureBar();
        b.classList.remove('is-active');
        void b.offsetWidth; // restart the animation if it was already running
        b.classList.add('is-active');
        window.clearTimeout(safety);
        safety = window.setTimeout(done, SAFETY_MS);
    }

    function done() {
        window.clearTimeout(safety);
        if (bar) {
            bar.classList.remove('is-active');
        }
    }

    function isPlainNavigation(link, event) {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return false;
        }
        if ((link.target && link.target !== '_self') || link.hasAttribute('download')) {
            return false;
        }
        var raw = link.getAttribute('href') || '';
        if (raw === '' || raw.charAt(0) === '#' || /^(mailto:|tel:|javascript:)/i.test(raw)) {
            return false;
        }
        var url;
        try {
            url = new URL(link.href, window.location.href);
        } catch (e) {
            return false;
        }
        if (url.origin !== window.location.origin) {
            return false;
        }
        return !(url.pathname === window.location.pathname && url.search === window.location.search && url.hash);
    }

    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('a[href]') : null;
        if (link && isPlainNavigation(link, event)) {
            // Other handlers (for example a confirm prompt) may cancel the click; look after they have run.
            window.setTimeout(function () {
                if (!event.defaultPrevented) {
                    start();
                }
            }, 0);
        }
    });

    document.addEventListener('submit', function (event) {
        window.setTimeout(function () {
            if (!event.defaultPrevented) {
                start();
            }
        }, 0);
    });

    window.addEventListener('pageshow', done);
})();
