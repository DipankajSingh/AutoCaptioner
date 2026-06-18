# Web Frontend Codebase Audit Findings

## Overview
A manual audit of the Web Frontend (`website/` directory) was conducted. The codebase uses Vite for building a static landing page. Below are the identified issues grouped by severity.

## Critical Severity

### 1. Unbounded `setInterval` for Animations
**File:** `website/main.js` (Lines 37-52)
**Observation:** The hero caption animation uses a `setInterval` loop to toggle the `.active` class every 800ms.
**Risk/Issue:** `setInterval` continues to run indefinitely even when the browser tab is inactive or hidden. This wastes CPU cycles, drains battery on mobile devices, and can cause jank when the user returns to the tab.
**Recommendation:** Refactor the animation to use CSS `@keyframes` which are natively optimized by the browser to pause when the tab is inactive. Alternatively, use `IntersectionObserver` to only run the interval when the `.caption-preview-box` is visible on screen.

## Major Severity

### 2. Unhandled Invalid Selectors in Smooth Scroll
**File:** `website/main.js` (Lines 23-35)
**Observation:** The smooth scroll script uses `document.querySelector(targetId)` where `targetId` is derived directly from the anchor's `href` attribute.
**Risk/Issue:** If an anchor has an `href` that starts with a number (e.g., `#1features`), `document.querySelector` will throw a `DOMException` for an invalid CSS selector, breaking all subsequent JavaScript execution on the page.
**Recommendation:** Replace `document.querySelector(targetId)` with `document.getElementById(targetId.substring(1))` which safely handles ID strings that begin with numbers.

### 3. Missing Fallbacks for Environment Variables
**File:** `website/main.js` (Lines 4-20) and `<script>` tags in `privacy.html` / `terms.html`
**Observation:** The code assigns links using `import.meta.env.VITE_PLAY_STORE_LINK` and `import.meta.env.VITE_CONTACT_EMAIL` directly.
**Risk/Issue:** If these environment variables are missing during the build process (e.g., in a CI/CD pipeline without the `.env` file configured), they will resolve to `undefined`. This results in the DOM displaying `mailto:undefined` or linking users to `https://yourdomain.com/undefined`, breaking core CTAs.
**Recommendation:** Implement fallbacks for these variables:
```javascript
const playStoreLink = import.meta.env.VITE_PLAY_STORE_LINK || '#';
const contactEmail = import.meta.env.VITE_CONTACT_EMAIL || 'support@autocaptioner.com';
```

## Minor / Best Practice Severity

### 4. Leftover Default Vite Boilerplate Code
**File:** `website/src/` directory
**Observation:** The `website/src/` folder contains files (`main.js`, `style.css`, `counter.js`, `assets/`) that are remnants of the default Vite project template.
**Risk/Issue:** While not included in the final build (because `vite.config.js` correctly targets the root `index.html`), these files create technical debt and clutter the repository.
**Recommendation:** Delete the `website/src/` directory entirely to maintain a clean codebase.

### 5. Broken Grid Layout in Subpage Footers
**File:** `website/privacy.html` (Lines 117-127) and `website/terms.html` (Lines 145-155)
**Observation:** The footer markup in the legal pages omits the `.footer-links` and `.footer-contact` sections found in `index.html`. However, the CSS grid for `.footer-container` in `style.css` is strictly defined as `grid-template-columns: 2fr 1fr 1fr;`.
**Risk/Issue:** This leaves empty grid columns and causes the `.footer-brand` section to abruptly truncate, leading to inconsistent layout behavior across pages.
**Recommendation:** Create a modifier class like `.footer-container-simple` for the subpages that uses `grid-template-columns: 1fr;`, or include the full footer navigation uniformly across all pages.

### 6. Missing Open Graph / SEO Metadata
**File:** `website/index.html`, `website/privacy.html`, `website/terms.html`
**Observation:** The HTML `<head>` lacks standard Open Graph (`og:title`, `og:description`, `og:image`) and Twitter Card metadata.
**Risk/Issue:** When users share the website link on social media platforms or messaging apps, the link preview will lack branding and context, reducing click-through rates.
**Recommendation:** Add standard Open Graph and Twitter Card `<meta>` tags to the `<head>` of all HTML files.

### 7. Missing Content Security Policy (CSP)
**File:** All HTML files
**Observation:** There is no `<meta http-equiv="Content-Security-Policy">` defined in the HTML files.
**Risk/Issue:** A CSP helps prevent Cross-Site Scripting (XSS) attacks. Without it, the site is theoretically more vulnerable if a vector were ever introduced.
**Recommendation:** Add a baseline CSP meta tag restricting scripts and styles to `self` and trusted domains (like Google Fonts).
