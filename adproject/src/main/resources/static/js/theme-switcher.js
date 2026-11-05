/**
 * IAMS Theme Switcher: Light, Dark, and Warm modes.
 * Persists user preference across navigation in localStorage and prevents FOUC.
 */
(function () {
    'use strict';

    const STORAGE_KEY = 'iams.theme';
    const VALID_THEMES = ['light', 'dark', 'warm'];

    // Retrieve saved theme or default to light
    function getSavedTheme() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved && VALID_THEMES.includes(saved)) {
                return saved;
            }
        } catch (_) {}
        return 'light';
    }

    // Apply theme to document
    function applyTheme(theme) {
        if (!VALID_THEMES.includes(theme)) theme = 'light';
        document.documentElement.setAttribute('data-theme', theme);
        if (document.body) {
            document.body.setAttribute('data-theme', theme);
        }

        // Update active status on all switcher buttons
        document.querySelectorAll('.theme-segment-btn, .theme-pill-btn').forEach(btn => {
            const btnTheme = btn.getAttribute('data-theme-set');
            const isActive = btnTheme === theme;
            btn.classList.toggle('active', isActive);
            btn.setAttribute('aria-pressed', String(isActive));
            btn.setAttribute('aria-checked', String(isActive));
        });

        window.dispatchEvent(new CustomEvent('iams:themechange', { detail: { theme } }));
    }

    // Immediately execute on parse to prevent flash of wrong theme
    const initialTheme = getSavedTheme();
    document.documentElement.setAttribute('data-theme', initialTheme);

    // Bind click events on interactive switcher buttons
    function bindThemeButtons() {
        const theme = getSavedTheme();
        applyTheme(theme);

        document.querySelectorAll('.theme-segment-btn, .theme-pill-btn').forEach(btn => {
            if (btn.dataset.bound) return;
            btn.dataset.bound = 'true';
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                const selected = this.getAttribute('data-theme-set');
                if (selected && VALID_THEMES.includes(selected)) {
                    try {
                        localStorage.setItem(STORAGE_KEY, selected);
                    } catch (_) {}
                    applyTheme(selected);
                }
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bindThemeButtons);
    } else {
        bindThemeButtons();
    }

    // Expose helper globally
    window.IAMS_Theme = {
        getTheme: getSavedTheme,
        setTheme: function (theme) {
            if (VALID_THEMES.includes(theme)) {
                try { localStorage.setItem(STORAGE_KEY, theme); } catch (_) {}
                applyTheme(theme);
            }
        }
    };
})();

