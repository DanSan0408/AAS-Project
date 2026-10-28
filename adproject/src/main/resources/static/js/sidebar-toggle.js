(function () {
    'use strict';
    const body = document.body;
    const toggle = document.querySelector('.sidebar-toggle');
    const overlay = document.querySelector('.sidebar-overlay');
    const sidebar = document.querySelector('.sidebar');
    if (!body || !toggle || !overlay || !sidebar || toggle.dataset.bound) return;
    toggle.dataset.bound = 'true';
    sidebar.id = 'system-sidebar';
    sidebar.setAttribute('role', 'navigation');
    sidebar.setAttribute('aria-label', 'Main navigation');
    toggle.setAttribute('aria-controls', sidebar.id);
    const desktop = window.matchMedia('(min-width: 1280px)');
    const storageKey = 'iams.sidebar.collapsed';
    let savedScroll = null;
    try { body.classList.toggle('sidebar-collapsed', localStorage.getItem(storageKey) === 'true'); } catch (_) {}

    function unlock() {
        if (savedScroll === null) return;
        body.style.position = body.style.top = body.style.left = body.style.right = body.style.width = '';
        window.scrollTo(0, savedScroll);
        savedScroll = null;
    }
    function sync() {
        const visible = desktop.matches
            ? !body.classList.contains('sidebar-collapsed') && !body.classList.contains('zen-mode')
            : body.classList.contains('sidebar-open');
        sidebar.inert = !visible;
        toggle.setAttribute('aria-expanded', String(visible));
        toggle.setAttribute('aria-label', visible ? 'Close navigation' : 'Expand navigation');
        toggle.title = visible ? 'Close navigation' : 'Expand navigation';
        const label = desktop.matches ? (visible ? 'Hide menu' : 'Show menu') : (visible ? 'Close menu' : 'Menu');
        const arrow = visible ? 'm15 9-3 3 3 3' : 'm12 9 3 3-3 3';
        toggle.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16"/><path d="' + arrow + '"/></svg><span class="sidebar-toggle-label">' + label + '</span>';
        const zenButton = document.getElementById('btn-zen-mode');
        if (zenButton) {
            zenButton.setAttribute('aria-pressed', String(body.classList.contains('zen-mode')));
            const label = zenButton.querySelector('span');
            if (label) label.textContent = body.classList.contains('zen-mode') ? 'Standard' : 'Zen Mode';
        }
    }
    function closeMobile() {
        const restoreFocus = sidebar.contains(document.activeElement);
        body.classList.remove('sidebar-open');
        unlock();
        sync();
        if (restoreFocus) toggle.focus();
    }
    toggle.addEventListener('click', function () {
        if (desktop.matches) {
            const wasHidden = body.classList.contains('sidebar-collapsed') || body.classList.contains('zen-mode');
            body.classList.remove('zen-mode');
            body.classList.toggle('sidebar-collapsed', !wasHidden);
            try { localStorage.setItem(storageKey, String(!wasHidden)); } catch (_) {}
        } else if (body.classList.contains('sidebar-open')) {
            closeMobile();
        } else {
            savedScroll = window.scrollY;
            body.style.position = 'fixed';
            body.style.top = '-' + savedScroll + 'px';
            body.style.left = body.style.right = '0';
            body.style.width = '100%';
            body.classList.add('sidebar-open');
        }
        sync();
        window.dispatchEvent(new Event('iams:layoutchange'));
    });
    overlay.addEventListener('click', closeMobile);
    sidebar.addEventListener('click', function (event) {
        if (!desktop.matches && event.target.closest('a[href]')) closeMobile();
    });
    document.addEventListener('keydown', function (event) {
        if (desktop.matches || !body.classList.contains('sidebar-open')) return;
        if (event.key === 'Escape') { closeMobile(); toggle.focus(); }
        if (event.key === 'Tab') {
            const items = [toggle, ...sidebar.querySelectorAll('a[href], button:not(:disabled), select:not(:disabled)')]
                .filter(el => el.getClientRects().length);
            const first = items[0], last = items[items.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    desktop.addEventListener('change', function () { closeMobile(); sync(); });
    window.addEventListener('iams:layoutchange', sync);
    sync();

    function mountNavigation() {
        const breadcrumb = document.querySelector('.system-breadcrumb');
        if (breadcrumb) {
            breadcrumb.classList.add('navigation-toolbar');
            breadcrumb.prepend(toggle);
            breadcrumb.querySelectorAll('.separator').forEach(el => el.setAttribute('aria-hidden', 'true'));
            breadcrumb.querySelectorAll('.assessment-name').forEach(el => { el.title = el.textContent.trim(); });
            const current = breadcrumb.querySelector('.current-page');
            if (current) current.setAttribute('aria-current', 'page');
        }
        highlightCurrentPage(breadcrumb);
        // Inline SVGs keep navigation recognisable without an external icon font.
        const icons = {
            dashboard: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
            assessment: '<rect x="5" y="5" width="14" height="16" rx="2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="m9 13 2 2 4-4M9 18h6"/>',
            comments: '<path d="M21 11a8 8 0 0 1-8 8H6l-4 3V11a9 9 0 0 1 19 0Z"/><path d="M7 10h10M7 14h6"/>',
            report: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z"/><path d="M14 3v6h6M12 18v-6m-3 3 3-3 3 3"/>',
            profile: '<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
            courses: '<path d="M12 5v16M3 4h5a4 4 0 0 1 4 2 4 4 0 0 1 4-2h5v15h-5a4 4 0 0 0-4 2 4 4 0 0 0-4-2H3Z"/>',
            users: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5"/>',
            progress: '<path d="M4 3v18h17M8 15l4-5 4 3 5-7"/>',
            switchRole: '<path d="M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4"/>'
        };
        sidebar.querySelectorAll('.menu-list a[href]').forEach(link => {
            if (link.querySelector('.sidebar-nav-icon')) return;
            const href = link.getAttribute('href');
            let kind = 'assessment';
            if (/home$/.test(href)) kind = /switch|back|view/i.test(link.textContent) ? 'switchRole' : 'dashboard';
            else if (/profile/.test(href)) kind = 'profile';
            else if (/progress|data-views|analytics/.test(href)) kind = 'progress';
            else if (/comments/.test(href)) kind = 'comments';
            else if (/submissions/.test(href)) kind = 'report';
            else if (/courses/.test(href)) kind = 'courses';
            else if (/users|students|lecturers|invite/.test(href)) kind = 'users';
            link.insertAdjacentHTML('afterbegin', '<svg class="sidebar-nav-icon" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' + icons[kind] + '</svg>');
            if (link.classList.contains('active')) link.setAttribute('aria-current', 'page');
        });
        sync();
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mountNavigation);
    else mountNavigation();

    function highlightCurrentPage(breadcrumb) {
        const path = window.location.pathname;
        const links = [...sidebar.querySelectorAll('.menu-list a[href]')];
        const parentHref = breadcrumb?.querySelector('ol a[href]')?.getAttribute('href');
        let current = links.filter(link => {
            const href = link.getAttribute('href');
            return path === href || (!href.endsWith('/home') && path.startsWith(href + '/'));
        }).sort((a, b) => b.getAttribute('href').length - a.getAttribute('href').length)[0];
        const mappedHref = /^\/student\/assessment\/.*\/(submit|submission)(\/|$)/.test(path) ? '/student/submissions'
            : /^\/student\/assessment\/.*\/evaluate\//.test(path) ? '/student/assessments' : parentHref;
        if (!current) current = links.find(link => link.getAttribute('href') === mappedHref);
        links.forEach(link => {
            link.classList.toggle('active', link === current);
            if (link === current) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
    }
    const form = document.getElementById('courseSwitchForm');
    const select = document.getElementById('courseSwitchSelect');
    if (form && select && !select.hasAttribute('onchange')) {
        select.addEventListener('change', function () { form.requestSubmit(); });
    }
})();
