/**
 * Assessor Rapid-Grading Keyboard Workstation
 * Enables keyboard shortcuts (1-5 to rate, J/K or Arrow Down/Up to navigate, Ctrl+Enter to save)
 * for rapid, seamless evaluation during student presentations.
 */
(function () {
    'use strict';

    let activeCardIndex = 0;
    let cards = [];

    function getCriterionCards() {
        return Array.from(document.querySelectorAll('.sub-rubric-card, .rubric-section[data-section-name]'))
            .filter(card => card.querySelectorAll('.rating-options input[type="radio"]').length > 0);
    }

    function setActiveCard(index, shouldScroll = true) {
        if (!cards.length) return;
        if (index < 0) index = 0;
        if (index >= cards.length) index = cards.length - 1;

        cards.forEach((c, idx) => {
            const isActive = idx === index;
            c.classList.toggle('keyboard-active-card', isActive);
            let badge = c.querySelector('.keyboard-card-indicator');
            if (isActive) {
                if (!badge) {
                    badge = document.createElement('span');
                    badge.className = 'keyboard-card-indicator';
                    badge.innerHTML = '<i class="fas fa-keyboard"></i> Active Criterion &bull; Press <strong>1-5</strong>';
                    c.prepend(badge);
                }
            } else if (badge) {
                badge.remove();
            }
        });

        activeCardIndex = index;

        if (shouldScroll && cards[index]) {
            cards[index].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    }

    function selectRatingInActiveCard(ratingNum) {
        const activeCard = cards[activeCardIndex];
        if (!activeCard) return;

        const radios = Array.from(activeCard.querySelectorAll('.rating-options input[type="radio"]'));
        if (!radios.length) return;

        const targetIndex = ratingNum - 1;
        if (targetIndex >= 0 && targetIndex < radios.length) {
            const radioToSelect = radios[targetIndex];
            radioToSelect.checked = true;
            radioToSelect.dispatchEvent(new Event('change', { bubbles: true }));

            // Visual feedback pulse on the selected option
            const label = radioToSelect.closest('.rating-option') || radioToSelect.parentElement;
            if (label) {
                label.classList.add('keyboard-selected-pulse');
                setTimeout(() => label.classList.remove('keyboard-selected-pulse'), 400);
            }

            // Auto-advance to the next criterion for fast workflow
            if (activeCardIndex < cards.length - 1) {
                setTimeout(() => {
                    setActiveCard(activeCardIndex + 1);
                }, 180);
            }
        }
    }

    function initKeyboardWorkstation() {
        cards = getCriterionCards();
        if (!cards.length) return;

        // Clicking anywhere in a card selects it as active
        cards.forEach((card, index) => {
            card.addEventListener('click', (e) => {
                if (e.target.matches('input, textarea, select')) return;
                setActiveCard(index, false);
            });
        });

        // Set initial active card
        setActiveCard(0, false);

        // Global keydown handler
        document.addEventListener('keydown', function (e) {
            // Ignore when typing in text fields or textareas
            const activeEl = document.activeElement;
            const isTyping = activeEl && (
                activeEl.tagName === 'TEXTAREA' ||
                (activeEl.tagName === 'INPUT' && !['radio', 'checkbox', 'button', 'submit'].includes(activeEl.type))
            );

            // Focus sidebar guide on ? if pressed
            if (e.key === '?' && !isTyping) {
                const guideBtn = document.querySelector('.hotkey-help-trigger');
                if (guideBtn) {
                    e.preventDefault();
                    guideBtn.focus();
                }
                return;
            }

            // Submit on Ctrl+Enter or Cmd+Enter
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                e.preventDefault();
                const submitBtn = document.querySelector('form#evaluationMainForm button[type="submit"]') ||
                                  document.querySelector('button[type="submit"]');
                if (submitBtn) {
                    submitBtn.click();
                }
                return;
            }

            if (isTyping) return;

            // Numbers 1 to 5 to rate
            if (['1', '2', '3', '4', '5'].includes(e.key)) {
                e.preventDefault();
                selectRatingInActiveCard(parseInt(e.key, 10));
                return;
            }

            // Navigation: ArrowDown, ArrowUp, J, K
            if (e.key === 'ArrowDown' || e.key === 'j' || e.key === 'J') {
                e.preventDefault();
                setActiveCard(activeCardIndex + 1);
            } else if (e.key === 'ArrowUp' || e.key === 'k' || e.key === 'K') {
                e.preventDefault();
                setActiveCard(activeCardIndex - 1);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initKeyboardWorkstation);
    } else {
        initKeyboardWorkstation();
    }
})();
