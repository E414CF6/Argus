import {DEFAULT_SETTINGS, type ExtensionSettings} from './types/settings';
import type {ChromeMessage} from './types/messages';

if (typeof window.argusInjected === 'undefined') {
    window.argusInjected = true;

    const OVERLAY_ID = 'argus-overlay';
    let currentSettings: ExtensionSettings = {...DEFAULT_SETTINGS};

    function hexToRgba(hex: string, opacity: number): string {
        const cleanHex = hex.replace('#', '');
        const r = parseInt(cleanHex.slice(0, 2), 16) || 0;
        const g = parseInt(cleanHex.slice(2, 4), 16) || 0;
        const b = parseInt(cleanHex.slice(4, 6), 16) || 0;
        return `rgba(${r}, ${g}, ${b}, ${opacity / 100})`;
    }

    async function loadSettings(): Promise<ExtensionSettings> {
        try {
            const data = await chrome.storage.local.get(DEFAULT_SETTINGS as unknown as { [key: string]: unknown });
            currentSettings = (data as unknown as ExtensionSettings) || DEFAULT_SETTINGS;
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
                box-sizing: border-box;
                font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                transition: opacity 0.2s ease;
                display: flex;
                flex-direction: column;
                user-select: text;
                -webkit-user-select: text;
            }
            #${OVERLAY_ID} * {
                box-sizing: border-box;
            }
            #${OVERLAY_ID} ::selection {
                background: rgba(99, 102, 241, 0.45);
                color: #ffffff;
            }
            #${OVERLAY_ID}.argus-hover-stealth {
                opacity: 0.18 !important;
            }
            #${OVERLAY_ID}.argus-hover-stealth:hover {
                opacity: 1 !important;
            }
            #${OVERLAY_ID}-drag-bar {
                height: 12px;
                width: 100%;
                cursor: grab;
                flex-shrink: 0;
                display: flex;
                align-items: center;
                justify-content: center;
                opacity: 0.3;
                transition: opacity 0.15s ease;
            }
            #${OVERLAY_ID}-drag-bar:hover {
                opacity: 0.9;
            }
            #${OVERLAY_ID}-drag-bar::after {
                content: '';
                width: 24px;
                height: 3px;
                background-color: currentColor;
                border-radius: 2px;
                opacity: 0.5;
            }
            #${OVERLAY_ID}-drag-bar:active {
                cursor: grabbing;
            }
            #${OVERLAY_ID}-body {
                padding: 4px 12px 10px 12px;
                overflow-y: auto;
                flex: 1;
                min-height: 0;
                scrollbar-width: thin;
                scrollbar-color: rgba(128, 128, 128, 0.2) transparent;
                user-select: text;
                -webkit-user-select: text;
            }
            #${OVERLAY_ID}-body::-webkit-scrollbar {
                width: 4px;
            }
            #${OVERLAY_ID}-body::-webkit-scrollbar-thumb {
                background: rgba(128, 128, 128, 0.25);
                border-radius: 2px;
            }
            #${OVERLAY_ID}-content {
                user-select: text;
                -webkit-user-select: text;
                cursor: text;
                white-space: pre-wrap;
                word-break: break-word;
                line-height: 1.45;
            }
            #${OVERLAY_ID}-mini-actions {
                position: absolute;
                top: 4px;
                right: 6px;
                display: flex;
                gap: 4px;
                opacity: 0;
                transition: opacity 0.15s ease;
                z-index: 10;
            }
            #${OVERLAY_ID}:hover #${OVERLAY_ID}-mini-actions {
                opacity: 0.75;
            }
            #${OVERLAY_ID}-mini-actions:hover {
                opacity: 1 !important;
            }
            .argus-mini-btn {
                background: rgba(0, 0, 0, 0.5);
                border: 1px solid rgba(255, 255, 255, 0.18);
                color: inherit;
                cursor: pointer;
                padding: 2px 6px;
                border-radius: 4px;
                font-size: 10px;
                line-height: 1;
                backdrop-filter: blur(4px);
                user-select: none;
            }
            .argus-mini-btn:hover {
                background: rgba(0, 0, 0, 0.8);
            }
            .argus-stealth-dot {
                display: inline-block;
                width: 6px;
                height: 6px;
                background-color: currentColor;
                border-radius: 50%;
                opacity: 0.5;
                animation: argus-pulse 1.2s infinite ease-in-out;
                margin-right: 6px;
            }
            @keyframes argus-pulse {
                0%, 100% { opacity: 0.2; transform: scale(0.8); }
                50% { opacity: 0.8; transform: scale(1.1); }
            }
        `;
        document.head.appendChild(style);
    }

    function makeDraggable(overlay: HTMLElement): void {
        let isDragging = false;
        let isResizing = false;
        let startX = 0, startY = 0;
        let origX = 0, origY = 0;

        overlay.addEventListener('mousedown', (e) => {
            const rect = overlay.getBoundingClientRect();
            // Check if click is near bottom-right corner for native-like resize
            if (e.clientX > rect.right - 16 && e.clientY > rect.bottom - 16) {
                isResizing = true;
                overlay.style.width = `${overlay.offsetWidth}px`;
                overlay.style.height = `${overlay.offsetHeight}px`;
                overlay.style.maxWidth = '100vw';
                overlay.style.maxHeight = '100vh';
                e.preventDefault();
                return;
            }

            // Don't drag if clicking buttons
            if ((e.target as HTMLElement)?.closest('.argus-mini-btn')) {
                return;
            }

            const dragBar = document.getElementById(`${OVERLAY_ID}-drag-bar`);
            const isDragBar = dragBar && (dragBar === e.target || dragBar.contains(e.target as Node));
            const isAltPressed = e.altKey || e.metaKey;

            // Only initiate window drag if:
            // 1. User is clicking the dedicated drag bar at the top, OR
            // 2. User is holding Alt / Meta key (force drag), OR
            // 3. User is clicking the outer padding of the overlay container (not inside the selectable text content)
            const contentEl = document.getElementById(`${OVERLAY_ID}-content`);
            const isInsideText = contentEl && (contentEl === e.target || contentEl.contains(e.target as Node));

            if (isDragBar || isAltPressed || !isInsideText) {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                origX = overlay.offsetLeft;
                origY = overlay.offsetTop;
                e.preventDefault(); // Prevent text highlight only when dragging the window
            }
            // Otherwise, allow native drag-to-select text!
        });

        document.addEventListener('mousemove', (e) => {
            if (!isDragging) return;
            const dx = e.clientX - startX;
            const dy = e.clientY - startY;
            const newX = Math.max(0, Math.min(window.innerWidth - overlay.offsetWidth, origX + dx));
            const newY = Math.max(0, Math.min(window.innerHeight - overlay.offsetHeight, origY + dy));
            overlay.style.left = `${newX}px`;
            overlay.style.top = `${newY}px`;
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                const newWidth = overlay.offsetWidth;
                const newHeight = overlay.offsetHeight;
                overlay.style.width = '';
                overlay.style.height = '';
                overlay.style.maxWidth = `${newWidth}px`;
                overlay.style.maxHeight = `${newHeight}px`;
                void saveSize(newWidth, newHeight);
            } else if (isDragging) {
                isDragging = false;
                void savePosition(overlay.offsetLeft, overlay.offsetTop);
            }
        });
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

        overlay.style.display = 'flex';

        // Hover Stealth Mode
        if (s.stealth_hoverOnly) {
            overlay.classList.add('argus-hover-stealth');
        } else {
            overlay.classList.remove('argus-hover-stealth');
        }

        // Positioning
        const defaultX = Math.max(12, window.innerWidth - (s.style_maxWidth || 380) - 16);
        const defaultY = Math.max(12, window.innerHeight - (s.style_maxHeight || 280) - 16);
        const posX = s.overlay_x >= 0 ? Math.min(s.overlay_x, window.innerWidth - 60) : defaultX;
        const posY = s.overlay_y >= 0 ? Math.min(s.overlay_y, window.innerHeight - 60) : defaultY;

        overlay.style.position = 'fixed';
        overlay.style.left = `${posX}px`;
        overlay.style.top = `${posY}px`;
        overlay.style.width = `${s.style_maxWidth}px`;
        overlay.style.maxWidth = `${s.style_maxWidth}px`;
        overlay.style.maxHeight = `${s.style_maxHeight}px`;
        overlay.style.background = hexToRgba(s.style_bgColor, s.style_bgOpacity);
        overlay.style.color = s.style_textColor;
        overlay.style.fontSize = `${s.style_fontSize}px`;
        overlay.style.borderRadius = s.stealth_mode ? '6px' : '8px';
        overlay.style.boxShadow = s.stealth_mode ? 'none' : '0 8px 24px rgba(0,0,0,0.3)';
        overlay.style.border = s.stealth_mode ? 'none' : '1px solid rgba(255,255,255,0.08)';
        overlay.style.backdropFilter = s.stealth_mode ? 'none' : 'blur(8px)';
        overlay.style.zIndex = '2147483647';
        overlay.style.resize = 'both';
        overlay.style.overflow = 'hidden';

        // Render Structure with a subtle top drag bar
        overlay.innerHTML = `
            <div id="${OVERLAY_ID}-drag-bar" title="Drag to move (or Alt+Drag anywhere)"></div>
            <div id="${OVERLAY_ID}-mini-actions">
                <button class="argus-mini-btn" id="${OVERLAY_ID}-btn-copy" title="Copy to clipboard">Copy</button>
                <button class="argus-mini-btn" id="${OVERLAY_ID}-btn-close" title="Close (Esc)">✕</button>
            </div>
            <div id="${OVERLAY_ID}-body">
                <div id="${OVERLAY_ID}-content"></div>
            </div>
        `;

        const contentEl = document.getElementById(`${OVERLAY_ID}-content`)!;
        const copyBtn = document.getElementById(`${OVERLAY_ID}-btn-copy`)!;
        const closeBtn = document.getElementById(`${OVERLAY_ID}-btn-close`)!;

        if (status === 'loading') {
            contentEl.innerHTML = `<span class="argus-stealth-dot"></span>${formatTextToHtml(text)}`;
        } else {
            contentEl.innerHTML = formatTextToHtml(text);
        }

        copyBtn.onclick = async (e) => {
            e.stopPropagation();
            try {
                // If user has selected specific text, copy that; otherwise copy entire content
                const selected = window.getSelection()?.toString();
                const textToCopy = selected && selected.trim() ? selected : (contentEl.innerText || text);
                await navigator.clipboard.writeText(textToCopy);
                copyBtn.textContent = '✓';
                setTimeout(() => {
                    copyBtn.textContent = 'Copy';
                }, 1200);
            } catch {
                copyBtn.textContent = '!';
            }
        };

        closeBtn.onclick = (e) => {
            e.stopPropagation();
            overlay.style.display = 'none';
        };

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
        overlay.style.display = overlay.style.display === 'none' ? 'flex' : 'none';
    }

    // Dismiss with Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            const overlay = document.getElementById(OVERLAY_ID);
            if (overlay && overlay.style.display !== 'none') {
                const activeTag = document.activeElement?.tagName.toLowerCase();
                if (activeTag !== 'input' && activeTag !== 'textarea') {
                    overlay.style.display = 'none';
                }
            }
        }
    });

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
            const overlay = document.getElementById(OVERLAY_ID);
            if (overlay) overlay.style.display = 'none';
            sendResponse({status: 'ok'});
        }

        return false;
    });

    // Live Settings Update
    chrome.storage.onChanged.addListener((changes, namespace) => {
        if (namespace === 'local') {
            const overlay = document.getElementById(OVERLAY_ID);
            if (overlay && overlay.style.display !== 'none') {
                const styleKeys = ['style_fontSize', 'style_textColor', 'style_bgColor', 'style_bgOpacity', 'style_maxWidth', 'style_maxHeight', 'stealth_mode', 'stealth_hoverOnly'];
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
}