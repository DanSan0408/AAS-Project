/**
 * ==========================================================================
 * IAMS Loading Animation & Transition Controller
 * Integrated Assessment Management System (IAMS)
 * ==========================================================================
 * Features:
 * - Institutional IAMS brand loading animation with logo breathing aura and progress shimmer
 * - Programmatic API: IAMSLoading.show(message), IAMSLoading.hide(), IAMSLoading.setMessage(msg)
 * - Auto-injects loading screen into DOM if not present (zero manual boilerplate needed)
 * - Seamless page load & link transitions with reduced-motion respect
 * - 100% backwards-compatible with UTMLoading alias
 */

(function(window, document) {
    'use strict';

    const DEFAULT_CONFIG = {
        logoUrl: '/images/IAMS_logo2.png',
        defaultMessage: 'Integrated Assessment Management System',
        duration: 0,               // Disabled animation for instant loading
        fadeOutTime: 0,            // Disabled fade out transition
        transitionDuration: 0,     // Disabled page exit fade duration
        navigationDelay: 0,        // Disabled navigation delay
        enablePageTransitions: false, // Disabled: no link interception
        autoInject: false,         // Disabled: do not inject loading overlay
        excludeSelectors: [
            'a[target="_blank"]',
            'a[download]',
            'a[href^="mailto:"]',
            'a[href^="tel:"]',
            'a[href^="#"]',
            'a[href^="javascript:"]',
            'a.no-transition',
            '.no-loading-transition'
        ]
    };

    const IAMSLoading = {
        config: { ...DEFAULT_CONFIG },
        screenElement: null,
        messageElement: null,
        initialized: false,

        /**
         * Initialize the loading controller
         */
        init: function(userOptions = {}) {
            if (this.initialized) return;
            this.config = { ...this.config, ...userOptions };

            this.screenElement = document.getElementById('loading-screen') || document.querySelector('.iams-loading-screen');

            // Animation disabled: ensure body is always marked loaded immediately and screen is hidden
            document.body.classList.add('loaded');

            if (this.screenElement) {
                this.screenElement.style.display = 'none';
                this.screenElement.classList.add('fade-out');
                this.screenElement.setAttribute('aria-busy', 'false');
            }

            this.initialized = true;
        },

        /**
         * Show the loading screen (Disabled: safe no-op)
         */
        show: function(message) {
            document.body.classList.add('loaded');
            if (this.screenElement) {
                this.screenElement.style.display = 'none';
                this.screenElement.classList.add('fade-out');
            }
        },

        /**
         * Set the status text while the loading screen is active
         */
        setMessage: function(message) {
            if (this.messageElement && message) {
                this.messageElement.textContent = message;
            }
        },

        /**
         * Hide the loading screen immediately
         */
        hide: function(callback) {
            document.body.classList.add('loaded');
            if (this.screenElement) {
                this.screenElement.style.display = 'none';
                this.screenElement.classList.add('fade-out');
                this.screenElement.setAttribute('aria-busy', 'false');
            }
            if (typeof callback === 'function') callback();
        },

        /**
         * Trigger a timed loading animation (useful for async actions)
         */
        trigger: function(duration = 1000, message = null, callback = null) {
            this.show(message);
            setTimeout(() => {
                this.hide(callback);
            }, duration);
        },

        /**
         * Create an in-component/inline loader element for widgets/cards
         */
        createInline: function(container, message = 'Loading data...') {
            if (!container) return null;
            const inlineEl = document.createElement('div');
            inlineEl.className = 'iams-loader-inline';
            inlineEl.setAttribute('role', 'status');
            inlineEl.innerHTML = `
                <div class="iams-loading-visual">
                    <div class="iams-loading-glow" aria-hidden="true"></div>
                    <div class="iams-loading-ring" aria-hidden="true"></div>
                    <img src="${this.config.logoUrl}" alt="IAMS Logo" class="iams-loading-logo" />
                </div>
                <div class="iams-loading-brand">IAMS</div>
                <div class="iams-loading-text">${message}</div>
                <div class="iams-loading-track" aria-hidden="true">
                    <div class="iams-loading-progress"></div>
                </div>
            `;
            container.innerHTML = '';
            container.appendChild(inlineEl);
            return inlineEl;
        },

        // ======================================================================
        // Private Internal Helpers
        // ======================================================================

        _createScreenElement: function() {
            const screen = document.createElement('div');
            screen.id = 'loading-screen';
            screen.className = 'iams-loading-screen';
            screen.setAttribute('role', 'status');
            screen.setAttribute('aria-live', 'polite');
            screen.setAttribute('aria-busy', 'true');
            screen.innerHTML = `
                <div class="iams-loading-container">
                    <div class="iams-loading-visual">
                        <div class="iams-loading-glow" aria-hidden="true"></div>
                        <div class="iams-loading-ring" aria-hidden="true"></div>
                        <img src="${this.config.logoUrl}" alt="IAMS Logo" class="iams-loading-logo" />
                    </div>
                    <div class="iams-loading-meta">
                        <div class="iams-loading-brand">IAMS</div>
                        <div class="iams-loading-text" id="iams-loading-msg">${this.config.defaultMessage}</div>
                        <div class="iams-loading-track" aria-hidden="true">
                            <div class="iams-loading-progress"></div>
                        </div>
                    </div>
                </div>
            `;
            return screen;
        },

        _handleInitialLoad: function() {
            const isTransitioning = sessionStorage.getItem('iams-transitioning') === 'true';
            if (isTransitioning) {
                sessionStorage.removeItem('iams-transitioning');
                setTimeout(() => this.hide(), 80);
            } else {
                if (document.readyState === 'complete') {
                    setTimeout(() => this.hide(), this.config.duration);
                } else {
                    window.addEventListener('load', () => {
                        setTimeout(() => this.hide(), this.config.duration);
                    });
                }
            }
        },

        _bindNavigationTransitions: function() {
            if (!this.config.enablePageTransitions) return;
            const self = this;

            document.addEventListener('click', function(e) {
                const link = e.target.closest('a');
                if (!link || self._shouldExclude(link)) return;

                const href = link.getAttribute('href');
                if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;

                // Only handle same-origin URLs
                if (link.hostname !== window.location.hostname) return;

                e.preventDefault();
                sessionStorage.setItem('iams-transitioning', 'true');
                self.show();

                setTimeout(() => {
                    window.location.href = link.href;
                }, self.config.navigationDelay);
            });

            // Handle back/forward cache restore
            window.addEventListener('pageshow', function(e) {
                if (e.persisted) {
                    self.hide();
                }
            });
        },

        _shouldExclude: function(link) {
            return this.config.excludeSelectors.some(selector => link.matches(selector));
        }
    };

    // Auto boot on DOM load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => IAMSLoading.init());
    } else {
        IAMSLoading.init();
    }

    // Expose global aliases (modern IAMSLoading + legacy UTMLoading)
    window.IAMSLoading = IAMSLoading;
    window.UTMLoading = IAMSLoading;

})(window, document);
