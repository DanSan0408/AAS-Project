(function () {
    'use strict';

    var body = document.body;
    var sidebarToggle = document.querySelector('.sidebar-toggle');
    var sidebarOverlay = document.querySelector('.sidebar-overlay');
    var lastScrollY = 0;

    if (!body || !sidebarToggle || !sidebarOverlay) {
        return;
    }

    // Guard against duplicate listener registration if loaded multiple times.
    if (sidebarToggle.dataset.bound === 'true') {
        return;
    }
    sidebarToggle.dataset.bound = 'true';

    function lockBodyScroll() {
        lastScrollY = window.scrollY || window.pageYOffset || 0;
        body.style.position = 'fixed';
        body.style.top = '-' + lastScrollY + 'px';
        body.style.left = '0';
        body.style.right = '0';
        body.style.width = '100%';
    }

    function unlockBodyScroll() {
        body.style.position = '';
        body.style.top = '';
        body.style.left = '';
        body.style.right = '';
        body.style.width = '';
        window.scrollTo(0, lastScrollY);
    }

    function closeSidebar() {
        body.classList.remove('sidebar-open');
        unlockBodyScroll();
        sidebarToggle.setAttribute('aria-expanded', 'false');
    }

    function toggleSidebar(event) {
        if (event) {
            event.preventDefault();
        }

        var willOpen = !body.classList.contains('sidebar-open');
        body.classList.toggle('sidebar-open', willOpen);

        if (willOpen) {
            lockBodyScroll();
        } else {
            unlockBodyScroll();
        }

        sidebarToggle.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
    }

    sidebarToggle.addEventListener('click', toggleSidebar);
    sidebarToggle.addEventListener('touchend', toggleSidebar, { passive: false });
    sidebarOverlay.addEventListener('click', closeSidebar);

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && body.classList.contains('sidebar-open')) {
            closeSidebar();
        }
    });

    window.addEventListener('resize', function () {
        if (window.innerWidth > 1279) {
            closeSidebar();
        }
    });

    var form = document.getElementById('courseSwitchForm');
    var select = document.getElementById('courseSwitchSelect');
    if (form && select) {
        // Standard POST ensures session state is saved before navigation.
        select.addEventListener('change', function () {
            form.submit();
        });
    }
})();
