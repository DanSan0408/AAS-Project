# IAMS Brand Guidelines & Visual Identity System
**Integrated Assessment Management System (IAMS)**  
*Official Reference Manual for Developers, Designers, and System Architects*

---

## 1. Brand Overview & Identity

### 1.1 Mission & Vision
The **Integrated Assessment Management System (IAMS)** is the institutional academic assessment platform engineered for university capstone and engineering design courses. IAMS centralizes rubrics, multi-assessor continuous evaluations, team peer reviews, deliverable submissions, and moderation analytics under a unified, accessible experience.

### 1.2 Brand Character & Aesthetic
- **Institutional & Trustworthy**: Clean academic aesthetic aligned with university design standards.
- **Handcrafted & Anti-Slop**: Purposeful whitespace, authentic typography, deliberate micro-interactions, and zero generic AI placeholders.
- **Role-Clear**: Immediate visual distinction across System Roles (Admin, Super Admin, Assessor/Lecturer, Academic Supervisor, Industrial Supervisor, Student) while maintaining strict global cohesiveness.

---

## 2. Logo Assets & Usage Standards

### 2.1 Asset Inventory

All official brand assets are cataloged in `docs/branding/assets/` and deployed in `adproject/src/main/resources/static/images/`:

| Asset File | Format | Resolution | Transparency | Primary Use Case |
| :--- | :--- | :--- | :--- | :--- |
| [`IAMS_logo2.png`](assets/IAMS_logo2.png) | PNG (8-bit RGBA) | 666 × 375 | Yes (Alpha) | **Primary System Mark**: Headers, loading animations, dialogs, credentials |
| [`IAMS_logo.jpeg`](assets/IAMS_logo.jpeg) | JPEG (JFIF) | 1280 × 720 | No (Solid) | High-resolution banners, presentation slides, report covers |
| [`utmlogo.png`](assets/utmlogo.png) | PNG (8-bit RGBA) | 887 × 300 | Yes (Alpha) | Institutional partnership mark, accreditation footer, certifications |
| [`logoUTM.png`](assets/logoUTM.png) | PNG (Colormap) | 2560 × 856 | Yes (Alpha) | High-DPI institutional co-branding headers |

### 2.2 Clear Space & Sizing
- **Clear Space Rule**: Maintain minimum clear space around the logo equal to the height of the letter **"I"** in the IAMS mark. No UI elements, text, borders, or busy patterns may encroach on this zone.
- **Minimum Digital Scale**: 
  - Desktop viewports: `160px` to `200px` width.
  - Mobile viewports (max-width 480px): `120px` to `140px` width.
  - Sizing must preserve the natural horizontal rectangular aspect ratio (`~16:9`). Never clip, squeeze, or force the logo into a square or circular container.

### 2.3 Permitted Background Pairings
- **Default Institutional Canvas** (`#F4F2EE`): Optimal contrast for primary headers and card surfaces.
- **Pure White** (`#FFFFFF`): Permitted on modal dialogs, loading containers, and clean card widgets.
- **Dark Theme Canvas** (`#121216`): Permitted with subtle luminous gold glow aura (`rgba(248, 166, 23, 0.35)`).
- **Warm Theme Canvas** (`#F5F0E8`): Permitted with warm earthy drop shadows.

### 2.4 Logo Don'ts (Violations)
- ❌ Do **not** stretch, skew, or distort the aspect ratio.
- ❌ Do **not** apply high-saturation neon drop shadows or bevels.
- ❌ Do **not** invert or recolor individual glyphs of the official logo mark.
- ❌ Do **not** crop the logo into a circle or oval.

---

## 3. Official Color System & Design Tokens

### 3.1 Primary Institutional Palette
The foundational palette reflects university heritage, academic rigor, and refined modern contrast:

| Color Name | Hex Code | RGB | CSS Variable | Semantic Role |
| :--- | :--- | :--- | :--- | :--- |
| **Institutional Maroon** | `#4A0018` | `rgb(74, 0, 24)` | `--ui-brand` / `--admin-primary` | Primary brand color, headers, admin buttons, key actions |
| **Maroon Light** | `#6B0025` | `rgb(107, 0, 37)` | `--ui-brand-light` | Hover states, active gradient highlights |
| **Maroon Dark** | `#2D000E` | `rgb(45, 0, 14)` | `--admin-primary-dark` | Deep contrast, dark mode borders, active pressed states |
| **Institutional Gold** | `#F8A617` | `rgb(248, 166, 23)` | `--admin-accent` / `--ui-accent` | Accent badges, progress indicators, orbital ring in loading |
| **Warm Amber** | `#D97706` | `rgb(217, 119, 6)` | `--ui-accent` | Warning states, high-contrast text on light backgrounds |

### 3.2 Role Signature Palette
Each system role possesses an assigned institutional signature color:

| Role | Color Name | Hex Code | Purpose |
| :--- | :--- | :--- | :--- |
| **Admin** | Maroon | `#4A0018` | System administration, user & rubric configuration |
| **Super Admin** | Imperial Maroon | `#380012` | Platform governance, course creation & assignment |
| **Assessor / Lecturer** | Forest Emerald | `#047857` | Grading workstation, student assessment, report marking |
| **Academic Supervisor** | Deep Navy | `#002B5B` | Continuous evaluation, supervisor logbooks |
| **Student** | Brick Crimson | `#7A1E2D` | Deliverable submission, peer reviews, comments |

### 3.3 Theme Tokens (Light, Dark, Warm)

```css
/* Light Mode (Default Institutional) */
:root {
    --ui-bg-canvas: #f4f2ee;
    --ui-bg-surface: #ffffff;
    --ui-border: #e2e8f0;
    --ui-text-primary: #18181b;
    --ui-text-secondary: #52525b;
}

/* Dark Theme */
html[data-theme="dark"] {
    --ui-bg-canvas: #121216;
    --ui-bg-surface: #1e1e24;
    --ui-border: rgba(255, 255, 255, 0.1);
    --ui-text-primary: #fafafa;
    --ui-text-secondary: #a1a1aa;
}

/* Warm Theme */
html[data-theme="warm"] {
    --ui-bg-canvas: #f5f0e8;
    --ui-bg-surface: #fffcf7;
    --ui-border: rgba(92, 60, 40, 0.14);
    --ui-text-primary: #2c2623;
    --ui-text-secondary: #6e6259;
}
```

---

## 4. Typography System

IAMS standardizes on self-hosted **Inter** variable font (`/css/fonts/inter/InterVariable.woff2`) for zero-external-dependency, GDPR-compliant institutional typography.

### 4.1 Strict Hierarchy & Weights (Admin Home Standard)
- **Regular Navigation Links**: `font-size: 15px !important; font-weight: 500 !important;` (Clean, un-bolded).
- **Sidebar Group Accordions**: `font-size: 0.9rem !important; font-weight: 600 !important;`.
- **Sidebar Submenu Items**: `font-size: 0.85rem !important; font-weight: 500 !important;`.
- **Active Navigation Items**: `font-weight: 600 !important;`.
- **Page Titles / Major Headings**: `font-weight: 700; font-size: 1.75rem - 2.2rem;`.
- **Body & Paragraph Copy**: `font-weight: 400; font-size: 0.95rem - 1rem; line-height: 1.5;`.
- **Micro-labels & Meta Text**: `font-weight: 500; font-size: 0.82rem - 0.85rem; color: #6b7280;`.

---

## 5. Official Loading Animation System

The IAMS loading animation communicates platform responsiveness, stability, and institutional elegance. It incorporates the official `IAMS_logo2.png` mark paired with a gentle luminous breathing aura, a dual-tone orbital accent ring, and an indeterminate gradient shimmer bar.

### 5.1 Visual Composition

```
        ┌───────────────────────────────────┐
        │                                   │
        │             ╭─────────╮           │  <-- Dual-tone orbital ring (2.4s spin)
        │           ╭─│         │─╮         │
        │          │  │  [IAMS] │  │        │  <-- Breathing logo (2.8s ease-in-out)
        │           ╰─│  [LOGO] │─╯         │  <-- Pulsing radial glow halo
        │             ╰─────────╯           │
        │                                   │
        │                IAMS               │  <-- Brand header (Maroon #4A0018)
        │    Integrated Assessment System   │  <-- Subtitle / dynamic status text
        │          [====■■■■====]           │  <-- Shimmer indeterminate progress bar
        │                                   │
        └───────────────────────────────────┘
```

### 5.2 HTML Architecture

```html
<div id="loading-screen" class="iams-loading-screen" role="status" aria-live="polite" aria-busy="true">
    <div class="iams-loading-container">
        <div class="iams-loading-visual">
            <div class="iams-loading-glow" aria-hidden="true"></div>
            <div class="iams-loading-ring" aria-hidden="true"></div>
            <img src="/images/IAMS_logo2.png" alt="IAMS Logo" class="iams-loading-logo" />
        </div>
        <div class="iams-loading-meta">
            <div class="iams-loading-brand">IAMS</div>
            <div class="iams-loading-text" id="iams-loading-msg">Integrated Assessment Management System</div>
            <div class="iams-loading-track" aria-hidden="true">
                <div class="iams-loading-progress"></div>
            </div>
        </div>
    </div>
</div>
```

### 5.3 CSS Implementation (`loading.css`)
Keyframes driving the animation:
- `@keyframes iamsLogoBreathe`: Smooth scale transition from `1.0` to `1.035` with gold halo shadow expansion.
- `@keyframes iamsGlowPulse`: Breathing ambient light from `0.92` to `1.12` scale with opacity modulation.
- `@keyframes iamsRingSpin`: 360-degree linear rotation of the gold-and-maroon bordered perimeter ring.
- `@keyframes iamsIndeterminateBar`: Smooth back-and-forth expansion across the progress track.

### 5.4 Programmatic JavaScript API (`loading.js`)

`loading.js` provides a globally accessible, zero-boilerplate API:

```javascript
// 1. Show loading screen with custom message
IAMSLoading.show('Calculating evaluation results...');

// 2. Update status message dynamically during multi-stage tasks
IAMSLoading.setMessage('Generating moderation report...');

// 3. Hide loading screen when operations finish
IAMSLoading.hide();

// 4. Trigger timed animation with callback
IAMSLoading.trigger(1500, 'Saving changes...', function() {
    console.log('Action complete!');
});

// 5. In-component / Inline card loader
const chartContainer = document.getElementById('chart-wrapper');
IAMSLoading.createInline(chartContainer, 'Loading analytics data...');
```

*Note: For backwards compatibility with legacy scripts, `window.UTMLoading` is fully aliased to `window.IAMSLoading`.*

### 5.5 Accessibility & Reduced Motion
In compliance with WCAG 2.1 Success Criterion 2.2.2 (Pause, Stop, Hide) and 2.3.3 (Animation from Interactions), the animation automatically adjusts when users enable system reduced-motion:

```css
@media (prefers-reduced-motion: reduce) {
    .iams-loading-logo,
    .iams-loading-glow,
    .iams-loading-ring,
    .iams-loading-progress {
        animation: none !important;
    }
    .iams-loading-ring {
        display: none;
    }
    .iams-loading-progress {
        width: 100%;
        left: 0;
    }
}
```

---

## 6. Template Integration Runbook

### Option A: Automatic JavaScript Auto-Injection (Zero HTML Boilerplate)
Simply include the stylesheet and script in your template `<head>` and `<body>`:

```html
<head>
    <link rel="stylesheet" th:href="@{/css/loading.css}">
</head>
<body>
    <!-- Your page content -->

    <script th:src="@{/js/loading.js}"></script>
</body>
```
*`IAMSLoading.init()` automatically checks if the loading container exists; if absent, it constructs and injects the branded overlay on demand.*

### Option B: Reusable Thymeleaf Fragment
Include the pre-rendered fragment explicitly:

```html
<body>
    <div th:replace="~{fragments/loading_screen :: loadingScreen}"></div>

    <!-- Your page content -->
    
    <script th:src="@{/js/loading.js}"></script>
</body>
```

---

## 7. Version & Maintenance
- **Version**: 2.0 (Institutional Standard)
- **Approved by**: Capstone Project System Architecture Team
- **Location**: [`docs/branding/`](file:///Users/dansan04/AAS-Project/docs/branding/) & [`adproject/docs/branding/`](file:///Users/dansan04/AAS-Project/adproject/docs/branding/)
