/**
 * IAMS PDF & Doodle Studio Engine
 * Continuous vertical scroll PDF reader with resolution-independent vector doodle overlays & flexible text annotations.
 */
(function(window) {
    'use strict';

    class PdfDoodleStudio {
        constructor(config) {
            this.container = typeof config.container === 'string' ? document.getElementById(config.container) : config.container;
            if (!this.container) {
                console.error('PdfDoodleStudio: container element not found.');
                return;
            }

            this.pdfUrl = config.pdfUrl;
            this.annotationsUrl = config.annotationsUrl;
            this.saveAnnotationUrl = config.saveAnnotationUrl || null;
            this.csrfToken = config.csrfToken || '';
            this.csrfHeader = config.csrfHeader || 'X-CSRF-TOKEN';
            this.readOnly = !!config.readOnly;
            this.fileName = config.fileName || 'report.pdf';

            // State
            this.pdfDoc = null;
            this.currentPageNum = 1;
            this.totalPageCount = 0;
            this.scale = 1.0;
            this.fitWidth = true;
            this.isRenderingAll = false;

            // Page references: pageNumber -> { container, pdfCanvas, doodleCanvas, pdfCtx, doodleCtx, viewport, width, height }
            this.pages = {};

            // Ink annotations state: pageNumber -> Array of strokes / text objects
            // Points are stored as fractional coordinates [0.0 - 1.0] relative to page dimensions
            this.annotationsByPage = {};
            this.undoStackByPage = {};
            this.redoStackByPage = {};
            this.dirtyPages = new Set();

            // Drawing properties
            this.currentTool = 'pen'; // 'pen', 'highlighter', 'text', 'eraser'
            this.currentColor = '#4A0018';
            this.currentSize = 4;
            this.isDrawing = false;
            this.activeDrawPage = null;
            this.currentStroke = null;
            this.autoSaveTimeout = null;

            // Text tool formatting state
            this.currentFontFamily = "'Inter', -apple-system, sans-serif";
            this.currentFontSize = 14;
            this.currentBold = false;
            this.currentItalic = false;

            // Text dragging and active editing state
            this.draggingText = null;
            this.hoveredText = null;
            this.activeTextEditor = null;
            this.activeEditingAnnotation = null;

            this.init();
        }

        async init() {
            this.buildDOM();
            this.attachEvents();
            await this.loadAnnotations();
            await this.loadPdf();
        }

        buildDOM() {
            this.container.innerHTML = `
                <div class="pdf-doodle-toolbar">
                    <div class="toolbar-group">
                        <button type="button" class="doodle-btn" id="btn-prev-page" title="Scroll to Previous Page">
                            <i class="fas fa-chevron-left"></i>
                        </button>
                        <span class="page-counter">
                            Page <span id="current-page-num">1</span> / <span id="total-page-count">--</span>
                        </span>
                        <button type="button" class="doodle-btn" id="btn-next-page" title="Scroll to Next Page">
                            <i class="fas fa-chevron-right"></i>
                        </button>
                    </div>

                    <div class="toolbar-group">
                        <button type="button" class="doodle-btn" id="btn-zoom-out" title="Zoom Out">
                            <i class="fas fa-search-minus"></i>
                        </button>
                        <button type="button" class="doodle-btn" id="btn-zoom-fit" title="Fit Width">
                            <i class="fas fa-arrows-alt-h"></i> Fit Width
                        </button>
                        <button type="button" class="doodle-btn" id="btn-zoom-in" title="Zoom In">
                            <i class="fas fa-search-plus"></i>
                        </button>
                    </div>

                    ${!this.readOnly ? `
                    <div class="toolbar-group">
                        <button type="button" class="doodle-btn active" id="tool-pen" data-tool="pen" title="Freehand Pen">
                            <i class="fas fa-pen"></i> Pen
                        </button>
                        <button type="button" class="doodle-btn" id="tool-highlighter" data-tool="highlighter" title="Highlighter">
                            <i class="fas fa-highlighter"></i> Highlight
                        </button>
                        <button type="button" class="doodle-btn" id="tool-text" data-tool="text" title="Text Box (Click on PDF to place text, drag to move)">
                            <i class="fas fa-font"></i> Text Box
                        </button>
                        <button type="button" class="doodle-btn" id="tool-eraser" data-tool="eraser" title="Eraser">
                            <i class="fas fa-eraser"></i> Eraser
                        </button>
                    </div>

                    <div class="toolbar-group color-swatch-group">
                        <button type="button" class="color-swatch active" data-color="#4A0018" style="background:#4A0018;" title="Maroon"></button>
                        <button type="button" class="color-swatch" data-color="#DC2626" style="background:#DC2626;" title="Red"></button>
                        <button type="button" class="color-swatch" data-color="#2563EB" style="background:#2563EB;" title="Blue"></button>
                        <button type="button" class="color-swatch" data-color="#047857" style="background:#047857;" title="Green"></button>
                        <button type="button" class="color-swatch" data-color="#000000" style="background:#000000;" title="Black"></button>
                        <button type="button" class="color-swatch" data-color="#F8A617" style="background:#F8A617;" title="Gold"></button>
                    </div>

                    <div class="toolbar-group stroke-size-group">
                        <button type="button" class="stroke-size-btn" data-size="2" title="Fine Stroke / Small Text">
                            <span class="stroke-size-dot" style="width:4px; height:4px;"></span>
                        </button>
                        <button type="button" class="stroke-size-btn active" data-size="4" title="Medium Stroke / Regular Text">
                            <span class="stroke-size-dot" style="width:7px; height:7px;"></span>
                        </button>
                        <button type="button" class="stroke-size-btn" data-size="8" title="Thick Stroke / Large Text">
                            <span class="stroke-size-dot" style="width:11px; height:11px;"></span>
                        </button>
                    </div>

                    <div class="toolbar-group">
                        <button type="button" class="doodle-btn" id="btn-undo" title="Undo (Ctrl+Z)" disabled>
                            <i class="fas fa-undo"></i>
                        </button>
                        <button type="button" class="doodle-btn" id="btn-redo" title="Redo (Ctrl+Y)" disabled>
                            <i class="fas fa-redo"></i>
                        </button>
                        <button type="button" class="doodle-btn" id="btn-clear" title="Clear Current Page Markups">
                            <i class="fas fa-trash-alt"></i> Clear Page
                        </button>
                        <button type="button" class="doodle-btn" id="btn-save" style="background:var(--doodle-primary); border-color:var(--doodle-accent); font-weight:600;">
                            <i class="fas fa-save"></i> Save Ink
                        </button>
                    </div>
                    ` : `
                    <div class="toolbar-group">
                        <span style="font-size: 0.82rem; color: #cbd5e1; display:flex; align-items:center; gap:6px;">
                            <i class="fas fa-eye" style="color:var(--doodle-accent);"></i> Viewing Assessor Feedback &amp; Ink
                        </span>
                    </div>
                    `}

                    <div class="toolbar-group">
                        <button type="button" class="doodle-btn" id="btn-export-annotated-pdf" style="background:#047857; border-color:#10b981; font-weight:600;" title="Download PDF with Assessor Markups &amp; Text Notes Baked In">
                            <i class="fas fa-file-download"></i> Download Annotated PDF
                        </button>
                        <span class="save-status-indicator saved" id="save-status">
                            <i class="fas fa-check-circle"></i> Ready
                        </span>
                    </div>
                </div>

                <div class="pdf-viewport" id="pdf-viewport">
                    <div class="pdf-continuous-scroll-wrapper" id="pdf-scroll-wrapper"></div>
                    <div class="pdf-loading-overlay" id="pdf-loading-overlay">
                        <div class="pdf-loading-spinner"></div>
                        <span id="pdf-loading-text">Loading report document...</span>
                    </div>
                </div>
            `;

            this.viewportEl = this.container.querySelector('#pdf-viewport');
            this.scrollWrapper = this.container.querySelector('#pdf-scroll-wrapper');
            this.loadingOverlay = this.container.querySelector('#pdf-loading-overlay');
            this.saveStatusEl = this.container.querySelector('#save-status');
        }

        attachEvents() {
            // Page navigation via scrolling
            this.container.querySelector('#btn-prev-page').addEventListener('click', () => this.scrollToPrevPage());
            this.container.querySelector('#btn-next-page').addEventListener('click', () => this.scrollToNextPage());

            // Scroll listener on viewport to update page indicator
            this.viewportEl.addEventListener('scroll', () => {
                this.updateCurrentPageFromScroll();
            });

            // Zoom
            this.container.querySelector('#btn-zoom-in').addEventListener('click', () => this.zoom(1.25));
            this.container.querySelector('#btn-zoom-out').addEventListener('click', () => this.zoom(0.8));
            this.container.querySelector('#btn-zoom-fit').addEventListener('click', () => this.zoomFit());

            // Download Annotated PDF
            const exportBtn = this.container.querySelector('#btn-export-annotated-pdf');
            if (exportBtn) {
                exportBtn.addEventListener('click', () => this.downloadAnnotatedPdf());
            }

            if (!this.readOnly) {
                // Tools
                this.container.querySelectorAll('[data-tool]').forEach(btn => {
                    btn.addEventListener('click', (e) => {
                        this.container.querySelectorAll('[data-tool]').forEach(b => b.classList.remove('active'));
                        const target = e.currentTarget;
                        target.classList.add('active');
                        this.currentTool = target.dataset.tool;
                        this.updateCanvasesToolClass();
                    });
                });

                // Colors
                this.container.querySelectorAll('.color-swatch').forEach(btn => {
                    btn.addEventListener('click', (e) => {
                        this.container.querySelectorAll('.color-swatch').forEach(b => b.classList.remove('active'));
                        const target = e.currentTarget;
                        target.classList.add('active');
                        this.currentColor = target.dataset.color;
                    });
                });

                // Stroke Sizes
                this.container.querySelectorAll('.stroke-size-btn').forEach(btn => {
                    btn.addEventListener('click', (e) => {
                        this.container.querySelectorAll('.stroke-size-btn').forEach(b => b.classList.remove('active'));
                        const target = e.currentTarget;
                        target.classList.add('active');
                        this.currentSize = parseInt(target.dataset.size, 10);
                    });
                });

                // Actions
                this.container.querySelector('#btn-undo').addEventListener('click', () => this.undo());
                this.container.querySelector('#btn-redo').addEventListener('click', () => this.redo());
                this.container.querySelector('#btn-clear').addEventListener('click', () => this.clearCurrentPage());
                this.container.querySelector('#btn-save').addEventListener('click', () => this.saveAnnotations(true));

                // Global Pointer up & move for smooth strokes & dragging
                window.addEventListener('pointermove', (e) => this.onGlobalPointerMove(e));
                window.addEventListener('pointerup', (e) => this.onGlobalPointerUp(e));

                // Keyboard shortcuts for Undo/Redo
                window.addEventListener('keydown', (e) => {
                    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
                    if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
                        e.preventDefault();
                        this.undo();
                    } else if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.shiftKey && e.key === 'Z'))) {
                        e.preventDefault();
                        this.redo();
                    }
                });
            }

            // Window resize: re-fit if in fitWidth mode
            let resizeTimer = null;
            window.addEventListener('resize', () => {
                if (resizeTimer) clearTimeout(resizeTimer);
                resizeTimer = setTimeout(() => {
                    if (this.fitWidth && this.pdfDoc) {
                        this.renderAllPages();
                    }
                }, 250);
            });
        }

        updateCanvasesToolClass() {
            Object.values(this.pages).forEach(p => {
                if (p.doodleCanvas) {
                    p.doodleCanvas.className = `doodle-page-canvas ${this.readOnly ? 'tool-readonly' : ('tool-' + this.currentTool)}`;
                }
            });
        }

        async loadPdf() {
            try {
                if (typeof pdfjsLib === 'undefined') {
                    throw new Error('PDF.js library is not loaded.');
                }
                pdfjsLib.GlobalWorkerOptions.workerSrc = '/js/pdfjs/pdf.worker.min.js';

                const loadingTask = pdfjsLib.getDocument(this.pdfUrl);
                this.pdfDoc = await loadingTask.promise;
                this.totalPageCount = this.pdfDoc.numPages;

                this.container.querySelector('#total-page-count').textContent = this.totalPageCount;
                this.updatePageNav();
                await this.renderAllPages();
                if (this.loadingOverlay) {
                    this.loadingOverlay.style.display = 'none';
                }
            } catch (err) {
                console.error('PdfDoodleStudio loadPdf error:', err);
                const textEl = this.container.querySelector('#pdf-loading-text');
                if (textEl) {
                    textEl.textContent = 'Failed to load PDF document: ' + err.message;
                }
            }
        }

        async loadAnnotations() {
            if (!this.annotationsUrl) return;
            try {
                const response = await fetch(this.annotationsUrl);
                if (response.ok) {
                    const data = await response.json();
                    if (Array.isArray(data)) {
                        data.forEach(ann => {
                            if (ann.pageNumber && ann.strokesJson) {
                                try {
                                    this.annotationsByPage[ann.pageNumber] = JSON.parse(ann.strokesJson);
                                } catch (e) {
                                    console.warn('Failed parsing strokes for page ' + ann.pageNumber, e);
                                }
                            }
                        });
                    }
                }
            } catch (err) {
                console.warn('Could not load existing annotations:', err);
            }
        }

        async renderAllPages() {
            if (this.isRenderingAll || !this.pdfDoc) return;
            this.isRenderingAll = true;

            // Close open text box if any
            this.closeTextEditor();

            this.scrollWrapper.innerHTML = '';
            this.pages = {};

            // Calculate scale
            if (this.fitWidth) {
                try {
                    const page1 = await this.pdfDoc.getPage(1);
                    const unscaled = page1.getViewport({ scale: 1.0 });
                    const targetWidth = Math.max(340, this.viewportEl.clientWidth - 48);
                    this.scale = Math.max(0.4, Math.min(2.5, targetWidth / unscaled.width));
                } catch (e) {
                    this.scale = 1.0;
                }
            }

            for (let p = 1; p <= this.totalPageCount; p++) {
                const page = await this.pdfDoc.getPage(p);
                const viewport = page.getViewport({ scale: this.scale });

                const pageContainer = document.createElement('div');
                pageContainer.className = 'pdf-page-container';
                pageContainer.id = 'page-container-' + p;
                pageContainer.dataset.pageNum = p;
                pageContainer.style.width = viewport.width + 'px';
                pageContainer.style.height = viewport.height + 'px';

                const pagePill = document.createElement('div');
                pagePill.className = 'page-pill';
                pagePill.textContent = `Page ${p} of ${this.totalPageCount}`;
                pageContainer.appendChild(pagePill);

                const pdfCanvas = document.createElement('canvas');
                pdfCanvas.className = 'pdf-page-canvas';
                pdfCanvas.width = viewport.width;
                pdfCanvas.height = viewport.height;
                pageContainer.appendChild(pdfCanvas);

                const doodleCanvas = document.createElement('canvas');
                doodleCanvas.className = `doodle-page-canvas ${this.readOnly ? 'tool-readonly' : ('tool-' + this.currentTool)}`;
                doodleCanvas.width = viewport.width;
                doodleCanvas.height = viewport.height;
                doodleCanvas.dataset.pageNum = p;
                pageContainer.appendChild(doodleCanvas);

                this.scrollWrapper.appendChild(pageContainer);

                const pdfCtx = pdfCanvas.getContext('2d');
                const doodleCtx = doodleCanvas.getContext('2d');

                this.pages[p] = {
                    pageNum: p,
                    container: pageContainer,
                    pdfCanvas: pdfCanvas,
                    doodleCanvas: doodleCanvas,
                    pdfCtx: pdfCtx,
                    doodleCtx: doodleCtx,
                    viewport: viewport,
                    width: viewport.width,
                    height: viewport.height
                };

                // Render PDF background
                page.render({
                    canvasContext: pdfCtx,
                    viewport: viewport
                });

                // Attach drawing and dragging pointer events
                if (!this.readOnly) {
                    this.attachPageEvents(doodleCanvas, p);
                }

                // Render existing vector ink strokes & text annotations
                this.redrawDoodlesForPage(p);
            }

            this.isRenderingAll = false;
            this.updateCurrentPageFromScroll();
            this.updateUndoRedoButtons();
        }

        attachPageEvents(canvas, pageNum) {
            canvas.addEventListener('pointerdown', (e) => this.onPagePointerDown(e, pageNum));
            canvas.addEventListener('pointermove', (e) => this.onPagePointerMove(e, pageNum));
        }

        updateCurrentPageFromScroll() {
            const viewportRect = this.viewportEl.getBoundingClientRect();
            let activePage = 1;
            let minDistance = Infinity;

            for (let p = 1; p <= this.totalPageCount; p++) {
                const pageInfo = this.pages[p];
                if (!pageInfo || !pageInfo.container) continue;

                const rect = pageInfo.container.getBoundingClientRect();
                const dist = Math.abs(rect.top - viewportRect.top);
                if (dist < minDistance) {
                    minDistance = dist;
                    activePage = p;
                }
            }

            if (activePage !== this.currentPageNum) {
                this.currentPageNum = activePage;
                const numEl = this.container.querySelector('#current-page-num');
                if (numEl) numEl.textContent = activePage;
                this.updatePageNav();
                this.updateUndoRedoButtons();
            }
        }

        scrollToPage(pageNum) {
            if (pageNum < 1 || pageNum > this.totalPageCount) return;
            const targetContainer = document.getElementById('page-container-' + pageNum);
            if (targetContainer) {
                targetContainer.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }

        scrollToPrevPage() {
            if (this.currentPageNum > 1) {
                this.scrollToPage(this.currentPageNum - 1);
            }
        }

        scrollToNextPage() {
            if (this.currentPageNum < this.totalPageCount) {
                this.scrollToPage(this.currentPageNum + 1);
            }
        }

        zoom(multiplier) {
            this.fitWidth = false;
            this.scale = Math.min(3.0, Math.max(0.4, this.scale * multiplier));
            this.renderAllPages();
        }

        zoomFit() {
            this.fitWidth = true;
            this.renderAllPages();
        }

        updatePageNav() {
            const prevBtn = this.container.querySelector('#btn-prev-page');
            const nextBtn = this.container.querySelector('#btn-next-page');
            if (prevBtn) prevBtn.disabled = this.currentPageNum <= 1;
            if (nextBtn) nextBtn.disabled = this.currentPageNum >= this.totalPageCount;
        }

        // =========================================================================
        // Doodle & Text Annotation Engine
        // =========================================================================

        getPageStrokes(pageNum) {
            if (!this.annotationsByPage[pageNum]) {
                this.annotationsByPage[pageNum] = [];
            }
            return this.annotationsByPage[pageNum];
        }

        getAllAnnotationsByPage() {
            return JSON.parse(JSON.stringify(this.annotationsByPage));
        }

        redrawDoodlesForPage(pageNum) {
            const pageInfo = this.pages[pageNum];
            if (!pageInfo || !pageInfo.doodleCtx) return;

            const ctx = pageInfo.doodleCtx;
            const w = pageInfo.doodleCanvas.width;
            const h = pageInfo.doodleCanvas.height;

            ctx.clearRect(0, 0, w, h);
            const strokes = this.getPageStrokes(pageNum);

            strokes.forEach(stroke => {
                if (stroke.tool === 'text') {
                    this.drawTextAnnotation(ctx, stroke, w, h);
                } else {
                    this.drawSingleStroke(ctx, stroke, w, h);
                }
            });
        }

        drawSingleStroke(ctx, stroke, w, h) {
            if (!stroke.points || stroke.points.length === 0) return;

            ctx.save();
            ctx.lineJoin = 'round';
            ctx.lineCap = stroke.tool === 'highlighter' ? 'square' : 'round';

            const size = stroke.size || 4;
            if (stroke.tool === 'highlighter') {
                ctx.globalAlpha = 0.35;
                ctx.lineWidth = size * 3.5;
                ctx.strokeStyle = stroke.color;
            } else if (stroke.tool === 'eraser') {
                ctx.globalCompositeOperation = 'destination-out';
                ctx.lineWidth = size * 4;
            } else {
                ctx.globalAlpha = 1.0;
                ctx.lineWidth = size;
                ctx.strokeStyle = stroke.color;
            }

            ctx.beginPath();
            const firstPt = stroke.points[0];
            ctx.moveTo(firstPt.x * w, firstPt.y * h);

            for (let i = 1; i < stroke.points.length; i++) {
                const pt = stroke.points[i];
                ctx.lineTo(pt.x * w, pt.y * h);
            }
            ctx.stroke();
            ctx.restore();
        }

        drawTextAnnotation(ctx, stroke, w, h, customScale = null) {
            const rawText = stroke.text || '';
            if (!rawText.trim()) return;

            ctx.save();
            const scaleFactor = customScale !== null ? customScale : (this.scale || 1.0);
            const baseSize = stroke.fontSize || stroke.size || 14;
            const fontSize = Math.max(10, Math.round(baseSize * scaleFactor));
            const fontWeight = stroke.bold ? '700' : '400';
            const fontStyle = stroke.italic ? 'italic' : 'normal';
            const fontFamily = stroke.fontFamily || "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";

            ctx.font = `${fontStyle} ${fontWeight} ${fontSize}px ${fontFamily}`;

            // Process text lines (respecting newlines & auto-wrapping)
            const paragraphs = rawText.split('\n');
            const maxBoxWidth = Math.min(380 * scaleFactor, w * 0.75);
            const lines = [];

            paragraphs.forEach(para => {
                const words = para.split(/\s+/);
                let currentLine = '';
                for (let i = 0; i < words.length; i++) {
                    const testLine = currentLine ? (currentLine + ' ' + words[i]) : words[i];
                    const metrics = ctx.measureText(testLine);
                    if (metrics.width > maxBoxWidth && currentLine !== '') {
                        lines.push(currentLine);
                        currentLine = words[i];
                    } else {
                        currentLine = testLine;
                    }
                }
                if (currentLine) lines.push(currentLine);
                if (words.length === 0 || (words.length === 1 && words[0] === '')) {
                    lines.push('');
                }
            });

            if (lines.length === 0) lines.push('');

            const lineHeight = fontSize * 1.35;
            const textWidths = lines.map(l => ctx.measureText(l).width);
            const textMaxW = Math.max(...textWidths, 24);
            const boxWidth = textMaxW + (8 * scaleFactor);
            const boxHeight = lines.length * lineHeight + (4 * scaleFactor);

            const rx = stroke.x * w;
            const ry = stroke.y * h;

            // Cache bounding box for fast hit detection, dragging, and hover cues
            stroke.cachedBox = {
                x: rx,
                y: ry,
                width: boxWidth,
                height: boxHeight
            };

            // Transparent background (No yellow sticky note background)
            // When user is hovering, dragging, or editing, show a subtle dashed focus outline
            const isInteracting = (!this.readOnly && (
                (this.draggingText && this.draggingText.annotation === stroke) ||
                (this.activeEditingAnnotation === stroke) ||
                (this.hoveredText === stroke)
            ));

            if (isInteracting) {
                ctx.save();
                ctx.setLineDash([4, 3]);
                ctx.strokeStyle = stroke.color || '#2563EB';
                ctx.lineWidth = 1.2;
                ctx.strokeRect(rx - 4, ry - 2, boxWidth + 8, boxHeight + 4);
                ctx.restore();
            }

            // Draw crisp lines of text directly onto document
            ctx.fillStyle = stroke.color || '#1e293b';
            lines.forEach((line, idx) => {
                ctx.fillText(
                    line,
                    rx,
                    ry + (idx + 1) * lineHeight - (fontSize * 0.22)
                );
            });

            ctx.restore();
        }

        getCanvasCoordinates(event, canvas) {
            const rect = canvas.getBoundingClientRect();
            const px = event.clientX - rect.left;
            const py = event.clientY - rect.top;

            return {
                x: Math.max(0, Math.min(1, px / canvas.width)),
                y: Math.max(0, Math.min(1, py / canvas.height)),
                rawPx: px,
                rawPy: py
            };
        }

        // Pointer Move on canvas for grab/hover cursor cues
        onPagePointerMove(e, pageNum) {
            if (this.isDrawing || this.draggingText || this.readOnly) return;
            const pageInfo = this.pages[pageNum];
            if (!pageInfo) return;

            const coords = this.getCanvasCoordinates(e, pageInfo.doodleCanvas);
            const hitText = this.findTextAnnotationAt(pageNum, coords);

            if (hitText) {
                if (this.hoveredText !== hitText) {
                    this.hoveredText = hitText;
                    this.redrawDoodlesForPage(pageNum);
                }
                pageInfo.doodleCanvas.style.cursor = 'grab';
            } else {
                if (this.hoveredText) {
                    this.hoveredText = null;
                    this.redrawDoodlesForPage(pageNum);
                }
                pageInfo.doodleCanvas.style.cursor = '';
            }
        }

        // Pointer Down on a specific page
        onPagePointerDown(e, pageNum) {
            if (this.readOnly || e.button !== 0) return;

            const pageInfo = this.pages[pageNum];
            if (!pageInfo) return;

            const coords = this.getCanvasCoordinates(e, pageInfo.doodleCanvas);

            // Check if user clicked on an existing text note on this page
            const existingText = this.findTextAnnotationAt(pageNum, coords);

            // CASE 1: ERASER TOOL
            if (this.currentTool === 'eraser') {
                this.closeTextEditor();
                this.isDrawing = true;
                this.activeDrawPage = pageNum;
                pageInfo.doodleCanvas.setPointerCapture(e.pointerId);
                this.eraseAt(pageNum, coords);
                return;
            }

            // CASE 2: CLICKED ON AN EXISTING TEXT BOX -> Initiate Draggable Move or Click to Edit
            if (existingText) {
                this.closeTextEditor();
                this.activeDrawPage = pageNum;
                this.draggingText = {
                    pageNum: pageNum,
                    annotation: existingText,
                    startClientX: e.clientX,
                    startClientY: e.clientY,
                    origX: existingText.x,
                    origY: existingText.y,
                    pointerId: e.pointerId,
                    hasMoved: false
                };
                pageInfo.doodleCanvas.setPointerCapture(e.pointerId);
                pageInfo.doodleCanvas.style.cursor = 'grabbing';
                this.redrawDoodlesForPage(pageNum);
                return;
            }

            // CASE 3: TEXT BOX TOOL ON EMPTY AREA -> Create New Text Box
            if (this.currentTool === 'text') {
                this.closeTextEditor();
                this.openTextEditor(pageNum, {
                    x: coords.x,
                    y: coords.y,
                    color: this.currentColor,
                    fontSize: this.currentFontSize || 14,
                    fontFamily: this.currentFontFamily || "'Inter', -apple-system, sans-serif",
                    bold: this.currentBold || false,
                    italic: this.currentItalic || false,
                    text: ''
                }, false);
                return;
            }

            // CASE 4: PEN OR HIGHLIGHTER
            this.closeTextEditor();
            this.activeDrawPage = pageNum;
            this.isDrawing = true;
            pageInfo.doodleCanvas.setPointerCapture(e.pointerId);

            this.currentStroke = {
                tool: this.currentTool,
                color: this.currentColor,
                size: this.currentSize,
                points: [{ x: coords.x, y: coords.y }]
            };

            this.pushUndo(pageNum);
            this.getPageStrokes(pageNum).push(this.currentStroke);
            this.dirtyPages.add(pageNum);
        }

        onGlobalPointerMove(e) {
            // Dragging text annotation around the page
            if (this.draggingText) {
                const pageNum = this.draggingText.pageNum;
                const pageInfo = this.pages[pageNum];
                if (!pageInfo) return;

                const dist = Math.hypot(e.clientX - this.draggingText.startClientX, e.clientY - this.draggingText.startClientY);
                if (dist > 3) {
                    this.draggingText.hasMoved = true;
                    const dx = (e.clientX - this.draggingText.startClientX) / pageInfo.width;
                    const dy = (e.clientY - this.draggingText.startClientY) / pageInfo.height;

                    this.draggingText.annotation.x = Math.max(0.005, Math.min(0.96, this.draggingText.origX + dx));
                    this.draggingText.annotation.y = Math.max(0.005, Math.min(0.96, this.draggingText.origY + dy));

                    this.redrawDoodlesForPage(pageNum);
                }
                return;
            }

            // Freehand pen/highlighter drawing or erasing
            if (!this.isDrawing || !this.activeDrawPage) return;
            const pageNum = this.activeDrawPage;
            const pageInfo = this.pages[pageNum];
            if (!pageInfo) return;

            const coords = this.getCanvasCoordinates(e, pageInfo.doodleCanvas);

            if (this.currentTool === 'eraser') {
                this.eraseAt(pageNum, coords);
            } else if (this.currentStroke) {
                this.currentStroke.points.push({ x: coords.x, y: coords.y });
                this.redrawDoodlesForPage(pageNum);
            }
        }

        onGlobalPointerUp(e) {
            // Handle completion of text box dragging or single click to edit
            if (this.draggingText) {
                const { pageNum, annotation, hasMoved, pointerId } = this.draggingText;
                const pageInfo = this.pages[pageNum];
                if (pageInfo && pageInfo.doodleCanvas) {
                    try { pageInfo.doodleCanvas.releasePointerCapture(pointerId); } catch(err){}
                    pageInfo.doodleCanvas.style.cursor = '';
                }
                this.draggingText = null;

                if (hasMoved) {
                    // Repositioning finished! Push history and schedule auto-save
                    this.pushUndo(pageNum);
                    this.dirtyPages.add(pageNum);
                    this.redrawDoodlesForPage(pageNum);
                    this.updateUndoRedoButtons();
                    this.setSaveStatus('dirty', 'Unsaved ink');
                    this.scheduleAutoSave();
                } else {
                    // User clicked on the note without dragging -> Open formatting editor!
                    this.openTextEditor(pageNum, annotation, true);
                }
                return;
            }

            if (!this.isDrawing) return;
            this.isDrawing = false;

            if (this.activeDrawPage) {
                const pageInfo = this.pages[this.activeDrawPage];
                if (pageInfo && pageInfo.doodleCanvas) {
                    try {
                        pageInfo.doodleCanvas.releasePointerCapture(e.pointerId);
                    } catch (err) {}
                }
            }

            this.currentStroke = null;
            this.activeDrawPage = null;
            this.updateUndoRedoButtons();
            this.setSaveStatus('dirty', 'Unsaved ink');
            this.scheduleAutoSave();
        }

        // =========================================================================
        // Text Box Floating Editor & Rich Typography
        // =========================================================================

        findTextAnnotationAt(pageNum, coords) {
            const pageInfo = this.pages[pageNum];
            if (!pageInfo) return null;
            const strokes = this.getPageStrokes(pageNum);

            for (let i = strokes.length - 1; i >= 0; i--) {
                const s = strokes[i];
                if (s.tool === 'text' && s.cachedBox) {
                    const b = s.cachedBox;
                    if (coords.rawPx >= (b.x - 6) && coords.rawPx <= (b.x + b.width + 6) &&
                        coords.rawPy >= (b.y - 4) && coords.rawPy <= (b.y + b.height + 4)) {
                        return s;
                    }
                }
            }
            return null;
        }

        openTextEditor(pageNum, annotation, isExisting) {
            this.closeTextEditor();
            const pageInfo = this.pages[pageNum];
            if (!pageInfo) return;

            this.activeEditingAnnotation = annotation;
            this.redrawDoodlesForPage(pageNum);

            const overlay = document.createElement('div');
            overlay.className = 'pdf-text-editor-overlay';

            // Calculate pixel position on canvas
            const posX = Math.min(pageInfo.width - 320, annotation.x * pageInfo.width);
            const posY = Math.min(pageInfo.height - 210, annotation.y * pageInfo.height);

            overlay.style.left = Math.max(10, posX) + 'px';
            overlay.style.top = Math.max(10, posY) + 'px';

            const activeFontFamily = annotation.fontFamily || this.currentFontFamily || "'Inter', -apple-system, sans-serif";
            const activeFontSize = annotation.fontSize || this.currentFontSize || 14;
            const isBold = annotation.bold !== undefined ? !!annotation.bold : !!this.currentBold;
            const isItalic = annotation.italic !== undefined ? !!annotation.italic : !!this.currentItalic;
            const activeColor = annotation.color || this.currentColor || '#4A0018';

            overlay.innerHTML = `
                <div class="editor-header">
                    <span><i class="fas fa-font"></i> ${isExisting ? 'Edit Text Box' : 'New Text Box'}</span>
                    <span class="editor-hint"><i class="fas fa-arrows-alt"></i> Draggable on page</span>
                </div>

                <div class="pdf-text-editor-toolbar">
                    <select class="fmt-select fmt-font-family" title="Font Family">
                        <option value="'Inter', -apple-system, sans-serif" ${activeFontFamily.includes('Inter') ? 'selected' : ''}>Sans (Inter)</option>
                        <option value="'Merriweather', Georgia, serif" ${activeFontFamily.includes('Merriweather') || activeFontFamily.includes('Georgia') ? 'selected' : ''}>Serif (Georgia)</option>
                        <option value="'Fira Code', 'Courier New', monospace" ${activeFontFamily.includes('Courier') || activeFontFamily.includes('monospace') ? 'selected' : ''}>Mono (Code)</option>
                    </select>

                    <select class="fmt-select fmt-font-size" title="Font Size">
                        <option value="12" ${activeFontSize == 12 ? 'selected' : ''}>12px</option>
                        <option value="14" ${activeFontSize == 14 ? 'selected' : ''}>14px</option>
                        <option value="18" ${activeFontSize == 18 ? 'selected' : ''}>18px</option>
                        <option value="24" ${activeFontSize == 24 ? 'selected' : ''}>24px</option>
                    </select>

                    <button type="button" class="btn-fmt-toggle btn-fmt-bold ${isBold ? 'active' : ''}" title="Bold (Ctrl+B)">
                        <i class="fas fa-bold"></i>
                    </button>
                    <button type="button" class="btn-fmt-toggle btn-fmt-italic ${isItalic ? 'active' : ''}" title="Italic (Ctrl+I)">
                        <i class="fas fa-italic"></i>
                    </button>

                    <div class="fmt-colors">
                        <button type="button" class="fmt-color-dot ${activeColor === '#4A0018' ? 'active' : ''}" data-color="#4A0018" style="background:#4A0018;" title="Maroon"></button>
                        <button type="button" class="fmt-color-dot ${activeColor === '#DC2626' ? 'active' : ''}" data-color="#DC2626" style="background:#DC2626;" title="Red"></button>
                        <button type="button" class="fmt-color-dot ${activeColor === '#2563EB' ? 'active' : ''}" data-color="#2563EB" style="background:#2563EB;" title="Blue"></button>
                        <button type="button" class="fmt-color-dot ${activeColor === '#047857' ? 'active' : ''}" data-color="#047857" style="background:#047857;" title="Green"></button>
                        <button type="button" class="fmt-color-dot ${activeColor === '#000000' ? 'active' : ''}" data-color="#000000" style="background:#000000;" title="Black"></button>
                        <button type="button" class="fmt-color-dot ${activeColor === '#F8A617' ? 'active' : ''}" data-color="#F8A617" style="background:#F8A617;" title="Gold"></button>
                    </div>
                </div>

                <textarea placeholder="Type your text comment here...">${annotation.text || ''}</textarea>

                <div class="pdf-text-editor-actions">
                    ${isExisting ? `<button type="button" class="btn-text-delete" title="Delete Text Box"><i class="fas fa-trash"></i> Delete</button>` : ''}
                    <button type="button" class="btn-text-cancel">Cancel</button>
                    <button type="button" class="btn-text-save"><i class="fas fa-check"></i> Apply</button>
                </div>
            `;

            pageInfo.container.appendChild(overlay);
            const textarea = overlay.querySelector('textarea');
            const fontFamilySelect = overlay.querySelector('.fmt-font-family');
            const fontSizeSelect = overlay.querySelector('.fmt-font-size');
            const boldBtn = overlay.querySelector('.btn-fmt-bold');
            const italicBtn = overlay.querySelector('.btn-fmt-italic');
            const colorDots = overlay.querySelectorAll('.fmt-color-dot');

            let curFamily = activeFontFamily;
            let curSize = activeFontSize;
            let curBold = isBold;
            let curItalic = isItalic;
            let curColor = activeColor;

            const updateTextareaStyle = () => {
                textarea.style.fontFamily = curFamily;
                textarea.style.fontSize = curSize + 'px';
                textarea.style.fontWeight = curBold ? '700' : '400';
                textarea.style.fontStyle = curItalic ? 'italic' : 'normal';
                textarea.style.color = curColor;
            };
            updateTextareaStyle();

            fontFamilySelect.addEventListener('change', () => {
                curFamily = fontFamilySelect.value;
                this.currentFontFamily = curFamily;
                updateTextareaStyle();
            });

            fontSizeSelect.addEventListener('change', () => {
                curSize = parseInt(fontSizeSelect.value, 10);
                this.currentFontSize = curSize;
                updateTextareaStyle();
            });

            boldBtn.addEventListener('click', () => {
                curBold = !curBold;
                boldBtn.classList.toggle('active', curBold);
                this.currentBold = curBold;
                updateTextareaStyle();
            });

            italicBtn.addEventListener('click', () => {
                curItalic = !curItalic;
                italicBtn.classList.toggle('active', curItalic);
                this.currentItalic = curItalic;
                updateTextareaStyle();
            });

            colorDots.forEach(dot => {
                dot.addEventListener('click', () => {
                    colorDots.forEach(d => d.classList.remove('active'));
                    dot.classList.add('active');
                    curColor = dot.dataset.color;
                    this.currentColor = curColor;
                    updateTextareaStyle();
                });
            });

            textarea.focus();
            if (annotation.text) {
                textarea.setSelectionRange(textarea.value.length, textarea.value.length);
            }

            const commitSave = () => {
                const text = textarea.value.trim();
                if (!text) {
                    if (isExisting) {
                        this.pushUndo(pageNum);
                        const strokes = this.getPageStrokes(pageNum);
                        const idx = strokes.indexOf(annotation);
                        if (idx !== -1) strokes.splice(idx, 1);
                        this.dirtyPages.add(pageNum);
                    }
                    this.closeTextEditor();
                    this.redrawDoodlesForPage(pageNum);
                    return;
                }

                this.pushUndo(pageNum);

                annotation.text = text;
                annotation.color = curColor;
                annotation.fontSize = curSize;
                annotation.fontFamily = curFamily;
                annotation.bold = curBold;
                annotation.italic = curItalic;

                if (!isExisting) {
                    annotation.tool = 'text';
                    this.getPageStrokes(pageNum).push(annotation);
                }

                this.dirtyPages.add(pageNum);
                this.closeTextEditor();
                this.redrawDoodlesForPage(pageNum);
                this.updateUndoRedoButtons();
                this.setSaveStatus('dirty', 'Unsaved ink');
                this.scheduleAutoSave();
            };

            const saveBtn = overlay.querySelector('.btn-text-save');
            const cancelBtn = overlay.querySelector('.btn-text-cancel');
            const deleteBtn = overlay.querySelector('.btn-text-delete');

            saveBtn.addEventListener('click', commitSave);
            cancelBtn.addEventListener('click', () => {
                this.closeTextEditor();
                this.redrawDoodlesForPage(pageNum);
            });

            if (deleteBtn) {
                deleteBtn.addEventListener('click', () => {
                    this.pushUndo(pageNum);
                    const strokes = this.getPageStrokes(pageNum);
                    const idx = strokes.indexOf(annotation);
                    if (idx !== -1) {
                        strokes.splice(idx, 1);
                    }
                    this.dirtyPages.add(pageNum);
                    this.closeTextEditor();
                    this.redrawDoodlesForPage(pageNum);
                    this.updateUndoRedoButtons();
                    this.setSaveStatus('dirty', 'Unsaved ink');
                    this.scheduleAutoSave();
                });
            }

            textarea.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    commitSave();
                } else if (e.key === 'Escape') {
                    this.closeTextEditor();
                    this.redrawDoodlesForPage(pageNum);
                }
            });

            overlay.commitFn = commitSave;
            this.activeTextEditor = overlay;
        }

        commitActiveTextEditor() {
            if (this.activeTextEditor && typeof this.activeTextEditor.commitFn === 'function') {
                this.activeTextEditor.commitFn();
            }
        }

        closeTextEditor() {
            if (this.activeTextEditor && this.activeTextEditor.parentNode) {
                this.activeTextEditor.parentNode.removeChild(this.activeTextEditor);
            }
            this.activeTextEditor = null;
            this.activeEditingAnnotation = null;
        }

        // =========================================================================
        // Eraser & History Engine
        // =========================================================================

        eraseAt(pageNum, coords) {
            const pageInfo = this.pages[pageNum];
            if (!pageInfo) return;

            const w = pageInfo.width;
            const h = pageInfo.height;
            const threshold = 20; // px radius
            const strokes = this.getPageStrokes(pageNum);

            let removed = false;
            const remaining = strokes.filter(stroke => {
                if (removed) return true;

                // Check text box collision
                if (stroke.tool === 'text' && stroke.cachedBox) {
                    const b = stroke.cachedBox;
                    if (coords.rawPx >= (b.x - 5) && coords.rawPx <= (b.x + b.width + 5) &&
                        coords.rawPy >= (b.y - 5) && coords.rawPy <= (b.y + b.height + 5)) {
                        removed = true;
                        return false;
                    }
                }

                // Check freehand stroke points collision
                if (stroke.points && stroke.points.length > 0) {
                    const hit = stroke.points.some(pt => {
                        const dx = (pt.x - coords.x) * w;
                        const dy = (pt.y - coords.y) * h;
                        return Math.hypot(dx, dy) < threshold;
                    });
                    if (hit) {
                        removed = true;
                        return false;
                    }
                }
                return true;
            });

            if (removed) {
                this.pushUndo(pageNum);
                this.annotationsByPage[pageNum] = remaining;
                this.dirtyPages.add(pageNum);
                this.redrawDoodlesForPage(pageNum);
                this.updateUndoRedoButtons();
                this.setSaveStatus('dirty', 'Unsaved ink');
                this.scheduleAutoSave();
            }
        }

        pushUndo(pageNum) {
            if (!this.undoStackByPage[pageNum]) {
                this.undoStackByPage[pageNum] = [];
            }
            this.undoStackByPage[pageNum].push(JSON.stringify(this.getPageStrokes(pageNum)));
            this.redoStackByPage[pageNum] = [];
        }

        undo() {
            const p = this.currentPageNum;
            const stack = this.undoStackByPage[p];
            if (!stack || stack.length === 0) return;

            if (!this.redoStackByPage[p]) this.redoStackByPage[p] = [];
            this.redoStackByPage[p].push(JSON.stringify(this.getPageStrokes(p)));

            const previousState = stack.pop();
            this.annotationsByPage[p] = JSON.parse(previousState);
            this.dirtyPages.add(p);
            this.redrawDoodlesForPage(p);
            this.updateUndoRedoButtons();
            this.scheduleAutoSave();
        }

        redo() {
            const p = this.currentPageNum;
            const stack = this.redoStackByPage[p];
            if (!stack || stack.length === 0) return;

            if (!this.undoStackByPage[p]) this.undoStackByPage[p] = [];
            this.undoStackByPage[p].push(JSON.stringify(this.getPageStrokes(p)));

            const nextState = stack.pop();
            this.annotationsByPage[p] = JSON.parse(nextState);
            this.dirtyPages.add(p);
            this.redrawDoodlesForPage(p);
            this.updateUndoRedoButtons();
            this.scheduleAutoSave();
        }

        clearCurrentPage() {
            if (this.readOnly) return;
            const p = this.currentPageNum;
            const strokes = this.getPageStrokes(p);
            if (strokes.length === 0) return;

            if (!confirm(`Clear all ink doodles and text notes on Page ${p}?`)) return;

            this.pushUndo(p);
            this.annotationsByPage[p] = [];
            this.dirtyPages.add(p);
            this.redrawDoodlesForPage(p);
            this.updateUndoRedoButtons();
            this.setSaveStatus('dirty', 'Unsaved ink');
            this.scheduleAutoSave();
        }

        updateUndoRedoButtons() {
            if (this.readOnly) return;
            const undoBtn = this.container.querySelector('#btn-undo');
            const redoBtn = this.container.querySelector('#btn-redo');

            const p = this.currentPageNum;
            const undoStack = this.undoStackByPage[p];
            const redoStack = this.redoStackByPage[p];

            if (undoBtn) undoBtn.disabled = !undoStack || undoStack.length === 0;
            if (redoBtn) redoBtn.disabled = !redoStack || redoStack.length === 0;
        }

        scheduleAutoSave() {
            if (this.autoSaveTimeout) clearTimeout(this.autoSaveTimeout);
            this.autoSaveTimeout = setTimeout(() => {
                this.saveAnnotations(false);
            }, 1200);
        }

        async saveAnnotations(isManual = false) {
            if (this.readOnly || !this.saveAnnotationUrl) return;
            if (this.dirtyPages.size === 0 && !isManual) return;

            // If manual, save current page at least
            if (isManual && this.dirtyPages.size === 0) {
                this.dirtyPages.add(this.currentPageNum);
            }

            this.setSaveStatus('saving', 'Saving ink...');

            try {
                const headers = { 'Content-Type': 'application/json' };
                if (this.csrfToken) {
                    headers[this.csrfHeader] = this.csrfToken;
                }

                const pagesToSave = Array.from(this.dirtyPages);
                for (const p of pagesToSave) {
                    const strokes = this.getPageStrokes(p);
                    await fetch(this.saveAnnotationUrl, {
                        method: 'POST',
                        headers: headers,
                        body: JSON.stringify({
                            pageNumber: p,
                            strokesJson: JSON.stringify(strokes)
                        })
                    });
                    this.dirtyPages.delete(p);
                }

                this.setSaveStatus('saved', 'Ink saved');
            } catch (err) {
                console.error('PdfDoodleStudio: error saving annotations', err);
                this.setSaveStatus('dirty', 'Error saving');
            }
        }

        async downloadAnnotatedPdf(filename = null) {
            const outName = filename || ('annotated_' + (this.fileName || 'report.pdf'));
            this.setSaveStatus('saving', 'Generating Annotated PDF...');

            try {
                let jsPdfLib = window.jspdf ? window.jspdf.jsPDF : (window.jsPDF || null);
                if (!jsPdfLib) {
                    await new Promise((resolve, reject) => {
                        const s = document.createElement('script');
                        s.src = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
                        s.onload = () => resolve();
                        s.onerror = () => reject(new Error('Failed to load jsPDF library'));
                        document.head.appendChild(s);
                    });
                    jsPdfLib = window.jspdf ? window.jspdf.jsPDF : window.jsPDF;
                }

                if (!jsPdfLib) {
                    throw new Error('jsPDF could not be initialized');
                }

                let doc = null;
                const exportScale = 1.5;

                for (let p = 1; p <= this.totalPageCount; p++) {
                    const page = await this.pdfDoc.getPage(p);
                    const viewport = page.getViewport({ scale: exportScale });

                    const exportCanvas = document.createElement('canvas');
                    exportCanvas.width = viewport.width;
                    exportCanvas.height = viewport.height;
                    const exportCtx = exportCanvas.getContext('2d');

                    // Render original PDF page at high resolution
                    await page.render({
                        canvasContext: exportCtx,
                        viewport: viewport
                    }).promise;

                    // Draw ink strokes & text annotations
                    const strokes = this.getPageStrokes(p);
                    strokes.forEach(stroke => {
                        if (stroke.tool === 'text') {
                            this.drawTextAnnotation(exportCtx, stroke, viewport.width, viewport.height, exportScale);
                        } else {
                            this.drawSingleStroke(exportCtx, stroke, viewport.width, viewport.height);
                        }
                    });

                    const ptWidth = viewport.width / exportScale;
                    const ptHeight = viewport.height / exportScale;
                    const orientation = ptWidth > ptHeight ? 'landscape' : 'portrait';

                    if (!doc) {
                        doc = new jsPdfLib({
                            orientation: orientation,
                            unit: 'pt',
                            format: [ptWidth, ptHeight]
                        });
                    } else {
                        doc.addPage([ptWidth, ptHeight], orientation);
                    }

                    const imgData = exportCanvas.toDataURL('image/jpeg', 0.92);
                    doc.addImage(imgData, 'JPEG', 0, 0, ptWidth, ptHeight);
                }

                doc.save(outName);
                this.setSaveStatus('saved', 'PDF Downloaded');
            } catch (err) {
                console.error('Error downloading annotated PDF:', err);
                alert('Could not generate annotated PDF: ' + err.message);
                this.setSaveStatus('dirty', 'Download failed');
            }
        }

        setSaveStatus(status, text) {
            if (!this.saveStatusEl) return;
            this.saveStatusEl.className = `save-status-indicator ${status}`;
            let icon = 'fa-check-circle';
            if (status === 'saving') icon = 'fa-spinner fa-spin';
            else if (status === 'dirty') icon = 'fa-exclamation-circle';

            this.saveStatusEl.innerHTML = `<i class="fas ${icon}"></i> ${text}`;
        }
    }

    window.PdfDoodleStudio = PdfDoodleStudio;

})(window);
