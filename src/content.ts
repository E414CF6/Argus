import {DEFAULT_SETTINGS, type ExtensionSettings} from './types/settings';
import type {ChromeMessage} from './types/messages';

if (typeof window.argusInjected === 'undefined') {
    window.argusInjected = true;

    const OVERLAY_ID = 'argus-overlay';
    const UNBLOCK_STYLE_ID = 'argus-unblock-drag-style';
    let currentSettings: ExtensionSettings = {...DEFAULT_SETTINGS};

    function hexToRgba(hex: string, opacity: number): string {
        const cleanHex = hex.replace('#', '');
        const r = parseInt(cleanHex.slice(0, 2), 16) || 0;
        const g = parseInt(cleanHex.slice(2, 4), 16) || 0;
        const b = parseInt(cleanHex.slice(4, 6), 16) || 0;
        const alpha = Math.max(0, Math.min(1, opacity / 100));
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }

    async function loadSettings(): Promise<ExtensionSettings> {
        try {
            const data = await chrome.storage.local.get(DEFAULT_SETTINGS as unknown as { [key: string]: unknown });
            currentSettings = (data as unknown as ExtensionSettings) || DEFAULT_SETTINGS;
            applyPageDragUnblocker(currentSettings.unblock_drag);
            return currentSettings;
        } catch {
            return DEFAULT_SETTINGS;
        }
    }

    async function savePosition(x: number, y: number): Promise<void> {
        currentSettings.overlay_x = x;
        currentSettings.overlay_y = y;
        await chrome.storage.local.set({overlay_x: x, overlay_y: y});
    }

    async function saveSize(width: number, height: number): Promise<void> {
        currentSettings.style_maxWidth = width;
        currentSettings.style_maxHeight = height;
        await chrome.storage.local.set({style_maxWidth: width, style_maxHeight: height});
    }

    /**
     * Complete Unblock Engine for copy-protected, drag-disabled, and contextmenu-locked web pages.
     */
    function applyPageDragUnblocker(enable = true): void {
        if (!enable) {
            const existingStyle = document.getElementById(UNBLOCK_STYLE_ID);
            if (existingStyle) existingStyle.remove();
            return;
        }

        // 1. Force selectable CSS on all elements
        let style = document.getElementById(UNBLOCK_STYLE_ID) as HTMLStyleElement | null;
        if (!style) {
            style = document.createElement('style');
            style.id = UNBLOCK_STYLE_ID;
            (document.head || document.documentElement).appendChild(style);
        }
        style.textContent = `
            *, *::before, *::after {
                -webkit-user-select: text !important;
                -moz-user-select: text !important;
                -ms-user-select: text !important;
                user-select: text !important;
                -webkit-touch-callout: default !important;
            }
        `;

        // 2. Clear inline event handler blockers
        try {
            document.onselectstart = null;
            document.ondragstart = null;
            document.oncontextmenu = null;
            document.oncopy = null;
            document.oncut = null;
            window.oncontextmenu = null;
            window.oncopy = null;
            window.onselectstart = null;
            if (document.body) {
                document.body.onselectstart = null;
                document.body.ondragstart = null;
                document.body.oncontextmenu = null;
                document.body.oncopy = null;
                document.body.oncut = null;
            }
        } catch {
            // Ignore restricted context
        }
    }

    // 3. Intercept and neutralize anti-copy / anti-drag capturing event listeners
    window.addEventListener('selectstart', (e) => {
        if (currentSettings.unblock_drag) {
            // Prevent hostile page script from blocking text selection start
            e.stopImmediatePropagation();
        }
    }, true);

    window.addEventListener('dragstart', (e) => {
        if (currentSettings.unblock_drag) {
            e.stopImmediatePropagation();
        }
    }, true);

    window.addEventListener('contextmenu', (e) => {
        if (currentSettings.unblock_drag) {
            // Prevent hostile page script from canceling right-click context menu
            e.stopImmediatePropagation();
        }
    }, true);

    // 4. Guaranteed Copy Handler: Ensure selected text is written to clipboard directly
    window.addEventListener('copy', (e) => {
        if (!currentSettings.unblock_drag) return;

        const selectedText = window.getSelection()?.toString();
        if (selectedText && selectedText.length > 0) {
            // Stop page scripts from overriding clipboard data with empty string or calling preventDefault()
            e.stopImmediatePropagation();
            if (e.clipboardData) {
                e.clipboardData.setData('text/plain', selectedText);
                e.preventDefault();
            }
        }
    }, true);

    // 5. Fallback Keyboard Copy Interceptor (Cmd+C / Ctrl+C)
    window.addEventListener('keydown', (e) => {
        if (!currentSettings.unblock_drag) return;

        const isCopyKey = (e.metaKey || e.ctrlKey) && (e.key === 'c' || e.key === 'C');
        if (isCopyKey) {
            const selectedText = window.getSelection()?.toString();
            if (selectedText && selectedText.length > 0) {
                // Try writing to clipboard directly in case page cancels the copy event
                navigator.clipboard?.writeText(selectedText).catch(() => {
                });
            }
        }
    }, true);

    function formatTextToHtml(raw: string): string {
        const escaped = raw
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // Minimalist markdown format
        const formattedBold = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        const formattedCode = formattedBold.replace(/`([^`]+)`/g, '<code style="background: rgba(128,128,128,0.18); padding: 1px 4px; border-radius: 3px; font-family: monospace; font-size: 0.9em;">$1</code>');

        const lines = formattedCode.split('\n');
        const processedLines = lines.map(line => {
            const trimmed = line.trim();
            if (trimmed.startsWith('* ') || trimmed.startsWith('- ')) {
                return `<div style="padding-left: 12px; position: relative;"><span style="position: absolute; left: 2px;">•</span>${trimmed.substring(2)}</div>`;
            }
            return line;
        });

        return processedLines.join('\n');
    }

    function ensureStylesInjected(): void {
        if (document.getElementById(`${OVERLAY_ID}-style`)) return;

        const style = document.createElement('style');
        style.id = `${OVERLAY_ID}-style`;
        style.textContent = `
            #${OVERLAY_ID} {
                box-sizing: border-box !important;
                font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                margin: 0 !important;
                padding: 4px 8px 8px 8px !important;
                flex-direction: column !important;
                user-select: text !important;
                -webkit-user-select: text !important;
                box-shadow: none !important;
                border: none !important;
                outline: none !important;
                position: fixed !important;
                pointer-events: auto !important;
            }
            #${OVERLAY_ID}.argus-hidden,
            #${OVERLAY_ID}[hidden] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }
            #${OVERLAY_ID}:not(.argus-hidden):not([hidden]) {
                display: flex !important;
            }
            #${OVERLAY_ID} * {
                box-sizing: border-box !important;
            }
            #${OVERLAY_ID} ::selection {
                background: rgba(99, 102, 241, 0.45) !important;
                color: #ffffff !important;
            }
            #${OVERLAY_ID}-header {
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                width: 100% !important;
                height: 14px !important;
                flex-shrink: 0 !important;
                margin-bottom: 2px !important;
                user-select: none !important;
                -webkit-user-select: none !important;
            }
            #${OVERLAY_ID}-drag-bar {
                height: 14px !important;
                flex: 1 !important;
                cursor: grab !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                opacity: 0.25 !important;
                transition: opacity 0.15s ease !important;
                padding: 2px 0 !important;
            }
            #${OVERLAY_ID}:hover #${OVERLAY_ID}-drag-bar,
            #${OVERLAY_ID}-drag-bar:hover {
                opacity: 0.8 !important;
            }
            #${OVERLAY_ID}-drag-bar::after {
                content: '' !important;
                width: 28px !important;
                height: 2px !important;
                background-color: currentColor !important;
                border-radius: 2px !important;
                opacity: 0.6 !important;
            }
            #${OVERLAY_ID}-drag-bar:active {
                cursor: grabbing !important;
            }
            #${OVERLAY_ID}-close-btn {
                width: 16px !important;
                height: 14px !important;
                cursor: pointer !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 14px !important;
                line-height: 1 !important;
                opacity: 0.25 !important;
                transition: opacity 0.15s ease, background 0.15s ease !important;
                border-radius: 3px !important;
                margin-left: 4px !important;
                color: currentColor !important;
            }
            #${OVERLAY_ID}:hover #${OVERLAY_ID}-close-btn {
                opacity: 0.6 !important;
            }
            #${OVERLAY_ID}-close-btn:hover {
                opacity: 1 !important;
                background: rgba(128, 128, 128, 0.25) !important;
            }
            #${OVERLAY_ID}-content {
                user-select: text !important;
                -webkit-user-select: text !important;
                cursor: text !important;
                white-space: pre-wrap !important;
                word-break: break-word !important;
                line-height: 1.4 !important;
                overflow-y: auto !important;
                flex: 1 !important;
                min-height: 0 !important;
                scrollbar-width: thin !important;
                scrollbar-color: rgba(128, 128, 128, 0.15) transparent !important;
                pointer-events: auto !important;
            }
            #${OVERLAY_ID}-content::-webkit-scrollbar {
                width: 3px !important;
            }
            #${OVERLAY_ID}-content::-webkit-scrollbar-thumb {
                background: rgba(128, 128, 128, 0.2) !important;
                border-radius: 2px !important;
            }
            #${OVERLAY_ID}-resizer {
                position: absolute !important;
                right: 1px !important;
                bottom: 1px !important;
                width: 14px !important;
                height: 14px !important;
                cursor: nwse-resize !important;
                user-select: none !important;
                -webkit-user-select: none !important;
                z-index: 100 !important;
                background: linear-gradient(135deg, transparent 0%, transparent 40%, currentColor 40%, currentColor 50%, transparent 50%, transparent 68%, currentColor 68%, currentColor 78%, transparent 78%, transparent 100%) !important;
                opacity: 0.35 !important;
                transition: opacity 0.15s ease !important;
            }
            #${OVERLAY_ID}:hover #${OVERLAY_ID}-resizer,
            #${OVERLAY_ID}-resizer:hover {
                opacity: 0.85 !important;
            }
            .argus-stealth-dot {
                display: inline-block !important;
                width: 4px !important;
                height: 4px !important;
                background-color: currentColor !important;
                border-radius: 50% !important;
                opacity: 0.4 !important;
                animation: argus-pulse 1.2s infinite ease-in-out !important;
                margin-right: 4px !important;
            }
            @keyframes argus-pulse {
                0%, 100% { opacity: 0.1; }
                50% { opacity: 0.6; }
            }
        `;
        (document.head || document.documentElement).appendChild(style);
    }

    function makeDraggable(overlay: HTMLElement): void {
        let isDragging = false;
        let isResizing = false;
        let startX = 0, startY = 0;
        let origX = 0, origY = 0;
        let startWidth = 0, startHeight = 0;

        overlay.addEventListener('mousedown', (e) => {
            const resizer = document.getElementById(`${OVERLAY_ID}-resizer`);
            const dragBar = document.getElementById(`${OVERLAY_ID}-drag-bar`);
            const closeBtn = document.getElementById(`${OVERLAY_ID}-close-btn`);
            const rect = overlay.getBoundingClientRect();

            // 0. Ignore if close button was clicked
            if (closeBtn && (closeBtn === e.target || closeBtn.contains(e.target as Node))) {
                return;
            }

            // 1. Resize Handle (bottom-right)
            const isResizerClick = resizer && (resizer === e.target || resizer.contains(e.target as Node));
            const isCornerClick = e.clientX > rect.right - 18 && e.clientY > rect.bottom - 18;

            if (isResizerClick || isCornerClick) {
                isResizing = true;
                startX = e.clientX;
                startY = e.clientY;
                origX = overlay.offsetLeft;
                origY = overlay.offsetTop;
                startWidth = overlay.offsetWidth;
                startHeight = overlay.offsetHeight;
                e.preventDefault();
                e.stopPropagation();
                return;
            }

            // 2. Drag & Reposition (Top drag bar, Alt/Shift/Meta key, or outer edges)
            const isDragBar = dragBar && (dragBar === e.target || dragBar.contains(e.target as Node));
            const isModifierPressed = e.altKey || e.shiftKey || e.metaKey || e.ctrlKey;
            const contentEl = document.getElementById(`${OVERLAY_ID}-content`);
            const isOutsideText = contentEl && (contentEl !== e.target && !contentEl.contains(e.target as Node));

            if (isDragBar || isModifierPressed || isOutsideText) {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                origX = overlay.offsetLeft;
                origY = overlay.offsetTop;
                document.body.style.cursor = 'grabbing';
                e.preventDefault();
                e.stopPropagation();
            }
            // Otherwise, native drag-to-select text works smoothly
        });

        window.addEventListener('mousemove', (e) => {
            if (isResizing) {
                const newWidth = Math.max(120, Math.min(window.innerWidth - origX - 10, startWidth + (e.clientX - startX)));
                const newHeight = Math.max(60, Math.min(window.innerHeight - origY - 10, startHeight + (e.clientY - startY)));
                overlay.style.width = `${newWidth}px`;
                overlay.style.height = `${newHeight}px`;
                overlay.style.maxWidth = `${newWidth}px`;
                overlay.style.maxHeight = `${newHeight}px`;
                return;
            }

            if (isDragging) {
                const dx = e.clientX - startX;
                const dy = e.clientY - startY;
                const newX = Math.max(0, Math.min(window.innerWidth - overlay.offsetWidth, origX + dx));
                const newY = Math.max(0, Math.min(window.innerHeight - overlay.offsetHeight, origY + dy));
                overlay.style.left = `${newX}px`;
                overlay.style.top = `${newY}px`;
            }
        }, {capture: true});

        window.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                const finalWidth = overlay.offsetWidth;
                const finalHeight = overlay.offsetHeight;
                void saveSize(finalWidth, finalHeight);
            } else if (isDragging) {
                isDragging = false;
                document.body.style.cursor = '';
                void savePosition(overlay.offsetLeft, overlay.offsetTop);
            }
        }, {capture: true});
    }

    function attachHeaderEvents(): void {
        const closeBtn = document.getElementById(`${OVERLAY_ID}-close-btn`);
        if (closeBtn) {
            closeBtn.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                hideOverlay();
            };
        }

        const dragBar = document.getElementById(`${OVERLAY_ID}-drag-bar`);
        if (dragBar) {
            dragBar.ondblclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                hideOverlay();
            };
        }
    }

    function isOverlayVisible(overlay: HTMLElement | null): boolean {
        if (!overlay) return false;
        if (overlay.classList.contains('argus-hidden') || overlay.hasAttribute('hidden')) {
            return false;
        }
        if (overlay.style.display === 'none') {
            return false;
        }
        const computed = window.getComputedStyle(overlay);
        return computed.display !== 'none' && computed.visibility !== 'hidden' && computed.opacity !== '0';
    }

    function hideOverlay(): void {
        const overlay = document.getElementById(OVERLAY_ID);
        if (overlay) {
            overlay.classList.add('argus-hidden');
            overlay.setAttribute('hidden', '');
            overlay.style.setProperty('display', 'none', 'important');
            overlay.style.setProperty('visibility', 'hidden', 'important');
        }
    }

    function showOverlayElement(overlay: HTMLElement): void {
        overlay.classList.remove('argus-hidden');
        overlay.removeAttribute('hidden');
        overlay.style.setProperty('display', 'flex', 'important');
        overlay.style.setProperty('visibility', 'visible', 'important');
    }

    async function showOverlay(text: string, status?: 'loading' | 'success' | 'error' | 'info'): Promise<void> {
        ensureStylesInjected();
        const s = await loadSettings();

        let overlay = document.getElementById(OVERLAY_ID);
        const isNew = !overlay;

        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = OVERLAY_ID;
            document.body.appendChild(overlay);
        }

        showOverlayElement(overlay);

        // Positioning
        const defaultX = Math.max(8, window.innerWidth - (s.style_maxWidth || 380) - 12);
        const defaultY = Math.max(8, window.innerHeight - (s.style_maxHeight || 280) - 12);
        const posX = s.overlay_x >= 0 ? Math.min(s.overlay_x, window.innerWidth - 40) : defaultX;
        const posY = s.overlay_y >= 0 ? Math.min(s.overlay_y, window.innerHeight - 40) : defaultY;

        overlay.style.left = `${posX}px`;
        overlay.style.top = `${posY}px`;
        overlay.style.width = `${s.style_maxWidth}px`;
        overlay.style.height = `${s.style_maxHeight}px`;
        overlay.style.maxWidth = `${s.style_maxWidth}px`;
        overlay.style.maxHeight = `${s.style_maxHeight}px`;
        overlay.style.background = hexToRgba(s.style_bgColor, s.style_bgOpacity);
        overlay.style.color = s.style_textColor;
        overlay.style.fontSize = `${s.style_fontSize}px`;
        overlay.style.borderRadius = s.style_bgOpacity > 0 ? '4px' : '0px';
        overlay.style.boxShadow = 'none';
        overlay.style.border = 'none';
        overlay.style.backdropFilter = 'none';
        overlay.style.zIndex = '2147483647';
        overlay.style.overflow = 'hidden';

        // Minimalist Layout: Header with drag bar & close button + Content + Resizer Hitbox
        overlay.innerHTML = `
            <div id="${OVERLAY_ID}-header">
                <div id="${OVERLAY_ID}-drag-bar" title="Drag to move (Double-click to hide)"></div>
                <div id="${OVERLAY_ID}-close-btn" title="Close overlay (Esc)">×</div>
            </div>
            <div id="${OVERLAY_ID}-content"></div>
            <div id="${OVERLAY_ID}-resizer" title="Drag corner to resize"></div>
        `;

        attachHeaderEvents();

        const contentEl = document.getElementById(`${OVERLAY_ID}-content`)!;

        if (status === 'loading') {
            contentEl.innerHTML = `<span class="argus-stealth-dot"></span>${formatTextToHtml(text)}`;
        } else {
            contentEl.innerHTML = formatTextToHtml(text);
        }

        if (isNew) {
            makeDraggable(overlay);
        }
    }

    function toggleOverlay(): void {
        const overlay = document.getElementById(OVERLAY_ID);
        if (!overlay) {
            void showOverlay('Ready. Press shortcut to capture.');
            return;
        }
        if (isOverlayVisible(overlay)) {
            hideOverlay();
        } else {
            showOverlayElement(overlay);
        }
    }

    // Dismiss with Escape key or keyboard shortcuts (Capturing phase ensures we beat hostile page listeners)
    window.addEventListener('keydown', (e) => {
        const overlay = document.getElementById(OVERLAY_ID);
        const overlayVisible = isOverlayVisible(overlay);

        // 1. Escape key: Dismiss overlay immediately if visible
        if (e.key === 'Escape') {
            if (overlayVisible) {
                hideOverlay();
                e.preventDefault();
                e.stopPropagation();
                return;
            }
        }

        const isShift = e.shiftKey;
        const isMod = e.metaKey || e.ctrlKey;
        const key = e.key.toLowerCase();

        // 2. Direct in-page fallback shortcut capture for toggling
        // Matches Cmd/Ctrl+Shift+X, Cmd/Ctrl+Shift+D, or Alt/Option+Shift+D
        if ((isShift && isMod && (key === 'x' || key === 'd')) || (isShift && e.altKey && key === 'd')) {
            toggleOverlay();
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        // 3. Direct in-page capture shortcut: Cmd/Ctrl+Shift+E
        if (isShift && isMod && key === 'e') {
            try {
                void chrome.runtime.sendMessage({type: 'REQUEST_CAPTURE'});
            } catch {
                // Ignore if runtime unavailable
            }
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        // 4. Direct in-page new session shortcut: Cmd/Ctrl+Shift+N
        if (isShift && isMod && key === 'n') {
            try {
                void chrome.runtime.sendMessage({type: 'REQUEST_NEW_SESSION'});
            } catch {
                // Ignore
            }
            e.preventDefault();
            e.stopPropagation();
            return;
        }
    }, true);

    // Message Listener
    chrome.runtime.onMessage.addListener((request: ChromeMessage, _sender, sendResponse) => {
        if (request.type === 'PING') {
            sendResponse({status: 'pong'});
            return false;
        }

        if (request.type === 'displayResult') {
            void showOverlay(request.text, request.status);
            sendResponse({status: 'ok'});
        } else if (request.type === 'toggleOverlay') {
            toggleOverlay();
            sendResponse({status: 'ok'});
        } else if (request.type === 'error') {
            void showOverlay(`Err: ${request.error}`, 'error');
            sendResponse({status: 'ok'});
        } else if (request.type === 'clearOverlay') {
            hideOverlay();
            sendResponse({status: 'ok'});
        }

        return false;
    });

    // Live Settings Update
    chrome.storage.onChanged.addListener((changes, namespace) => {
        if (namespace === 'local') {
            if (changes.unblock_drag) {
                applyPageDragUnblocker(Boolean(changes.unblock_drag.newValue));
            }

            const overlay = document.getElementById(OVERLAY_ID);
            if (overlay && isOverlayVisible(overlay)) {
                const styleKeys = ['style_fontSize', 'style_textColor', 'style_bgColor', 'style_bgOpacity', 'style_maxWidth', 'style_maxHeight', 'stealth_mode'];
                const hasStyleChange = Object.keys(changes).some(key => styleKeys.includes(key));
                if (hasStyleChange) {
                    const contentEl = document.getElementById(`${OVERLAY_ID}-content`);
                    if (contentEl) {
                        void showOverlay(contentEl.innerText || '');
                    }
                }
            }
        }
    });

    // Initial load
    void loadSettings();
}