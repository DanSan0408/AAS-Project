/**
 * UTM CAS - Centralized Loading Animation Script
 * 
 * Features:
 * - Loading screen on page load/refresh with no white flash
 * - Smooth page transitions between pages (no double loading)
 * - Optimized for speed and smoothness
 * 
 * Usage:
 * 1. Include critical.css FIRST in head
 * 2. Include loading.css after critical.css
 * 3. Include this script before closing body tag
 */

const UTMLoading = {
    config: {
        // ===== CUSTOMIZE THESE DURATIONS =====
        
        duration: 500,              // Page load/refresh: How long to show loading (milliseconds)
                                     // Recommended: 800-1500ms
        
        fadeOutTime: 600,            // How long loading screen takes to fade out (milliseconds)
                                     // Recommended: 400-800ms
        
        transitionDuration: 400,     // Page navigation: How long content fades out (milliseconds)
                                     // Recommended: 300-500ms
        
        navigationDelay: 400,        // Page navigation: Wait before navigating (milliseconds)
                                     // Recommended: 300-500ms
        
        crossfadeOverlap: 200,       // Overlap between loading fade-out and content fade-in (milliseconds)
                                     // Recommended: 150-300ms
        
        // ===== END CUSTOMIZATION =====
        
        enablePageTransitions: true, // Auto-intercept internal links
        excludeSelectors: [
            'a[target="_blank"]',
            'a[download]',
            'a[href^="mailto:"]',
            'a[href^="tel:"]',
            'a[href^="#"]',
            'a.no-transition',
            'form'
        ],
        onComplete: null
    },

    init: function(options = {}) {
        this.config = { ...this.config, ...options };
        this.loadingScreen = document.getElementById('loading-screen');
        this.pageContent = document.querySelector('.page-content');

        if (!this.loadingScreen) {
            console.warn('Loading screen not found');
            document.body.classList.add('loaded');
            return;
        }

        // Check if coming from page transition
        const isTransitioning = sessionStorage.getItem('utm-transitioning') === 'true';
        
        if (isTransitioning) {
            sessionStorage.removeItem('utm-transitioning');
            // Skip the loading animation - just fade in content immediately
            // The loading screen is already visible from critical.css
            setTimeout(() => {
                this.hide();
            }, 100); // Very brief delay, then fade in content
        } else {
            // Normal page load/refresh - show full loading animation
            // Initialize page transitions
            if (this.config.enablePageTransitions) {
                this.initPageTransitions();
            }

            // Auto-hide loading screen after duration
            if (document.readyState === 'complete') {
                this.hide();
            } else {
                window.addEventListener('load', () => this.hide());
            }
        }
    },

    initPageTransitions: function() {
        const self = this;
        
        // Intercept internal link clicks
        document.addEventListener('click', function(e) {
            const link = e.target.closest('a');
            if (!link || self.shouldExcludeLink(link) || !self.isInternalLink(link)) {
                return;
            }
            
            e.preventDefault();
            self.navigateWithTransition(link.href);
        });

        // Handle browser back/forward cache
        window.addEventListener('pageshow', function(e) {
            if (e.persisted) {
                document.body.classList.remove('loaded');
                if (self.loadingScreen) {
                    self.loadingScreen.classList.remove('fade-out');
                    self.loadingScreen.style.opacity = '1';
                }
                setTimeout(() => self.hide(), 100);
            }
        });
    },

    shouldExcludeLink: function(link) {
        return this.config.excludeSelectors.some(selector => link.matches(selector));
    },

    isInternalLink: function(link) {
        return link.hostname === window.location.hostname;
    },

    navigateWithTransition: function(url) {
        const self = this;
        
        // Don't do anything to the loading screen - it stays visible via CSS
        // Just fade out the content and navigate
        
        // Fade out current content smoothly
        if (this.pageContent) {
            this.pageContent.style.transition = `opacity ${this.config.transitionDuration}ms cubic-bezier(0.25, 0.46, 0.45, 0.94)`;
            this.pageContent.style.opacity = '0';
        }
        
        // Navigate after smooth fade
        // The new page will load with loading screen already visible (from critical.css)
        setTimeout(() => {
            sessionStorage.setItem('utm-transitioning', 'true');
            window.location.href = url;
        }, this.config.navigationDelay);
    },

    show: function() {
        if (this.loadingScreen) {
            document.body.classList.remove('loaded');
            this.loadingScreen.classList.remove('fade-out');
            this.loadingScreen.style.opacity = '1';
        }
    },

    hide: function() {
        const self = this;
        
        setTimeout(() => {
            // Mark as loaded - content will start fading in
            document.body.classList.add('loaded');

            // Slight delay before fading out loading screen for overlap effect
            setTimeout(() => {
                if (self.loadingScreen) {
                    self.loadingScreen.classList.add('fade-out');
                    
                    // Hide loading screen after fade completes
                    setTimeout(() => {
                        self.loadingScreen.style.display = 'none';
                    }, self.config.fadeOutTime);
                }
            }, self.config.crossfadeOverlap);

            // Callback after content is visible
            if (typeof self.config.onComplete === 'function') {
                setTimeout(() => {
                    self.config.onComplete();
                }, 400);
            }
        }, this.config.duration);
    },

    trigger: function(duration = null, callback = null) {
        this.show();
        const loadDuration = duration !== null ? duration : this.config.duration;
        
        setTimeout(() => {
            this.hide();
            if (callback) callback();
        }, loadDuration);
    },

    goTo: function(url) {
        this.navigateWithTransition(url);
    }
};

// Auto-initialize
(function() {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => UTMLoading.init());
    } else {
        UTMLoading.init();
    }
})();

window.UTMLoading = UTMLoading;