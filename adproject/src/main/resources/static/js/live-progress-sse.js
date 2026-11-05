/**
 * Real-Time Presentation Progress Tracker (SSE)
 * Connects to Server-Sent Events to stream live presentation evaluation progress,
 * supports dynamic single-card assessment switching for multiple concurrent evaluations,
 * and provides collapsible detail drawers for drill-down and multi-assessment overview.
 */
(function () {
    'use strict';

    function initLiveProgress() {
        const tracker = document.querySelector('.live-progress-card');
        if (!tracker || tracker.dataset.bound) return;
        tracker.dataset.bound = 'true';

        let currentAssessmentId = tracker.getAttribute('data-assessment-id');
        if (!currentAssessmentId) return;

        const titleLabel = tracker.querySelector('.active-assessment-title');
        const progressBar = tracker.querySelector('.live-progress-fill');
        const percentLabel = tracker.querySelector('.live-progress-percent');
        const countLabel = tracker.querySelector('.live-progress-count');
        const updatedLabel = tracker.querySelector('.live-progress-updated');
        const toggleBtn = tracker.querySelector('.btn-toggle-details');
        const detailsContainer = tracker.querySelector('.live-progress-details-drawer');
        const detailsTbody = tracker.querySelector('.live-details-tbody');
        const assessmentSelect = tracker.querySelector('#liveAssessmentSelect');
        const toggleMultiBtn = tracker.querySelector('#btnToggleMultiSummary');
        const multiDrawer = tracker.querySelector('#liveMultiSummaryDrawer');

        let currentEventSource = null;

        // Toggle Assessor Details
        if (toggleBtn && detailsContainer) {
            toggleBtn.addEventListener('click', function (e) {
                e.preventDefault();
                const isHidden = detailsContainer.style.display === 'none' || !detailsContainer.classList.contains('is-expanded');
                if (isHidden) {
                    detailsContainer.style.display = 'block';
                    detailsContainer.classList.add('is-expanded');
                    toggleBtn.innerHTML = '<i class="fas fa-chevron-up"></i> Hide Details';
                    toggleBtn.setAttribute('aria-expanded', 'true');
                } else {
                    detailsContainer.style.display = 'none';
                    detailsContainer.classList.remove('is-expanded');
                    toggleBtn.innerHTML = '<i class="fas fa-chevron-down"></i> Open Details';
                    toggleBtn.setAttribute('aria-expanded', 'false');
                }
            });
        }

        // Toggle Multi-Assessment Overview
        if (toggleMultiBtn && multiDrawer) {
            toggleMultiBtn.addEventListener('click', function (e) {
                e.preventDefault();
                const isHidden = multiDrawer.style.display === 'none' || !multiDrawer.classList.contains('is-expanded');
                if (isHidden) {
                    multiDrawer.style.display = 'block';
                    multiDrawer.classList.add('is-expanded');
                    toggleMultiBtn.setAttribute('aria-expanded', 'true');
                    refreshMultiSummarySnapshots();
                } else {
                    multiDrawer.style.display = 'none';
                    multiDrawer.classList.remove('is-expanded');
                    toggleMultiBtn.setAttribute('aria-expanded', 'false');
                }
            });
        }

        // Fetch snapshots for all rows in the multi-assessment drawer
        function refreshMultiSummarySnapshots() {
            if (!multiDrawer) return;
            const rows = multiDrawer.querySelectorAll('.multi-summary-row');
            rows.forEach(function (row) {
                const id = row.getAttribute('data-assessment-id');
                if (!id) return;
                fetch('/api/progress/snapshot/' + encodeURIComponent(id))
                    .then(res => res.ok ? res.json() : null)
                    .then(data => {
                        if (!data) return;
                        const pct = Math.min(100, Math.max(0, data.overallPercent || 0));
                        const fill = row.querySelector('.multi-summary-bar-fill');
                        const pctText = row.querySelector('.multi-summary-pct');
                        if (fill) fill.style.width = pct + '%';
                        if (pctText) pctText.textContent = pct + '%';
                    })
                    .catch(err => console.debug('Snapshot fetch error for', id, err));
            });
        }

        // Assessment Selector change listener
        if (assessmentSelect) {
            assessmentSelect.addEventListener('change', function () {
                const newId = this.value;
                const newTitle = this.options[this.selectedIndex] ? this.options[this.selectedIndex].text : '';
                switchAssessment(newId, newTitle);
            });
        }

        // Switch button clicks inside multi-summary drawer
        if (multiDrawer) {
            multiDrawer.addEventListener('click', function (e) {
                const switchBtn = e.target.closest('.btn-switch-live');
                if (!switchBtn) return;
                e.preventDefault();
                const targetId = switchBtn.getAttribute('data-assessment-id');
                const targetTitle = switchBtn.getAttribute('data-title');
                if (targetId) {
                    if (assessmentSelect) {
                        assessmentSelect.value = targetId;
                    }
                    switchAssessment(targetId, targetTitle);
                }
            });
        }

        function switchAssessment(newId, newTitle) {
            if (!newId || newId === currentAssessmentId && currentEventSource) return;

            // Clean up existing stream
            if (currentEventSource) {
                try {
                    currentEventSource.close();
                } catch (err) {
                    console.debug('Error closing SSE stream:', err);
                }
                currentEventSource = null;
            }

            currentAssessmentId = newId;
            tracker.setAttribute('data-assessment-id', newId);

            if (titleLabel && newTitle) {
                titleLabel.textContent = newTitle.replace(/\s*\([^)]*\)\s*$/, '');
            }

            // Reset visual progress
            if (progressBar) progressBar.style.width = '0%';
            if (percentLabel) percentLabel.textContent = '0%';
            if (countLabel) countLabel.textContent = 'Connecting to evaluation stream...';
            if (detailsTbody) {
                detailsTbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted" style="padding: 12px;">Connecting to live evaluation status...</td></tr>';
            }

            // Fast-path snapshot fetch
            fetch('/api/progress/snapshot/' + encodeURIComponent(newId))
                .then(res => res.ok ? res.json() : null)
                .then(data => {
                    if (data && tracker.getAttribute('data-assessment-id') === newId) {
                        updateUI(data);
                    }
                })
                .catch(err => console.debug('Snapshot error:', err));

            // Connect new SSE stream
            connectStream(newId);
        }

        function updateUI(data) {
            if (!data) return;

            const percent = Math.min(100, Math.max(0, data.overallPercent || 0));
            if (progressBar) progressBar.style.width = percent + '%';
            if (percentLabel) percentLabel.textContent = percent + '%';
            if (countLabel) {
                countLabel.textContent = (data.completedAssessors || 0) + ' / ' + (data.totalAssessors || 0) + ' Assessors Completed';
            }
            if (updatedLabel && data.lastUpdated) {
                updatedLabel.textContent = 'Updated ' + data.lastUpdated;
            }

            // Render table details
            if (detailsTbody && Array.isArray(data.details)) {
                if (data.details.length === 0) {
                    detailsTbody.innerHTML = '<tr><td colspan="4" class="text-muted text-center" style="padding: 12px;">No assessors assigned yet.</td></tr>';
                } else {
                    detailsTbody.innerHTML = data.details.map(row => {
                        const prog = Math.min(100, Math.max(0, row.progress || 0));
                        const status = row.status || 'Not Started';
                        let badgeClass = 'badge-secondary';
                        if (status === 'Completed') badgeClass = 'badge-success';
                        else if (status === 'In Progress') badgeClass = 'badge-warning';

                        return `
                            <tr>
                                <td><strong>${escapeHtml(row.lecturerName || 'Unknown')}</strong></td>
                                <td>${escapeHtml(row.assignedTarget || '-')}</td>
                                <td><span class="status-badge ${badgeClass}">${escapeHtml(status)}</span></td>
                                <td style="width: 140px;">
                                    <div class="mini-progress-track">
                                        <div class="mini-progress-fill" style="width: ${prog}%"></div>
                                    </div>
                                    <small style="color: var(--ui-muted); font-size: 0.72rem;">${prog}%</small>
                                </td>
                            </tr>
                        `;
                    }).join('');
                }
            }
        }

        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        function connectStream(id) {
            try {
                const streamUrl = '/api/progress/stream/' + encodeURIComponent(id);
                currentEventSource = new EventSource(streamUrl);

                currentEventSource.addEventListener('progress-init', function (e) {
                    try {
                        const data = JSON.parse(e.data);
                        updateUI(data);
                    } catch (err) {
                        console.debug('Error parsing progress-init payload:', err);
                    }
                });

                currentEventSource.addEventListener('progress-update', function (e) {
                    try {
                        const data = JSON.parse(e.data);
                        updateUI(data);
                        // Flash live pulse
                        const pulse = tracker.querySelector('.sse-live-dot');
                        if (pulse) {
                            pulse.classList.add('pulse-active');
                            setTimeout(() => pulse.classList.remove('pulse-active'), 1000);
                        }
                    } catch (err) {
                        console.debug('Error parsing progress-update payload:', err);
                    }
                });

                currentEventSource.onerror = function () {
                    // EventSource will auto-reconnect
                };
            } catch (err) {
                console.debug('SSE initialization error:', err);
            }
        }

        // Start initial stream
        connectStream(currentAssessmentId);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initLiveProgress);
    } else {
        initLiveProgress();
    }
})();
