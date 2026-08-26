import { DEFAULT_SETTINGS, type ExtensionSettings } from './types/settings';
import type { ChromeMessage } from './types/messages';

if (typeof window.argusInjected === 'undefined') {
    window.argusInjected = true;

    const OVERLAY_ID = 'argus-overlay';
    let currentSettings: ExtensionSettings = { ...DEFAULT_SETTINGS };

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
            return currentSettings;
        } catch {
            return DEFAULT_SETTINGS;
        }
    }

    async function savePosition(x: number, y: number): Promise<void> {
        currentSettings.overlay_x = x;
        currentSettings.overlay_y = y;
        await chrome.storage.local.set({ overlay_x: x, overlay_y: y });
    }

    async function saveSize(width: number, height: number): Promise<void> {
        currentSettings.style_maxWidth = width;
        currentSettings.style_maxHeight = height;
        await chrome.storage.local.set({ style_maxWidth: width, style_maxHeight: height });
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
                margin: 0;
                padding: 6px 8px;
                display: flex;
                flex-direction: column;
                user-select: text;
                -webkit-user-select: text;
                box-shadow: none;
                border: none;
                outline: none;
            }
            #${OVERLAY_ID} * {
                box-sizing: border-box;
            }
            #${OVERLAY_ID} ::selection {
                background: rgba(99, 102, 241, 0.45);
                color: #ffffff;
            }
            #${OVERLAY_ID}-content {
                user-select: text;
                -webkit-user-select: text;
                cursor: text;
                white-space: pre-wrap;
                word-break: break-word;
                line-height: 1.4;
                overflow-y: auto;
                flex: 1;
                min-height: 0;
                scrollbar-width: thin;
                scrollbar-color: rgba(128, 128, 128, 0.15) transparent;
            }
            #${OVERLAY_ID}-content::-webkit-scrollbar {
                width: 3px;
            }
            #${OVERLAY_ID}-content::-webkit-scrollbar-thumb {
                background: rgba(128, 128, 128, 0.2);
                border-radius: 2px;
            }
            .argus-stealth-dot {
                display: inline-block;
                width: 4px;
                height: 4px;
                background-color: currentColor;
                border-radius: 50%;
                opacity: 0.4;
                animation: argus-pulse 1.2s infinite ease-in-out;
                margin-right: 4px;
            }
            @keyframes argus-pulse {
                0%, 100% { opacity: 0.1; }
                50% { opacity: 0.6; }
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
            // Check if click is near bottom-right corner for resize handle
            if (e.clientX > rect.right - 14 && e.clientY > rect.bottom - 14) {
                isResizing = true;
                overlay.style.width = `${overlay.offsetWidth}px`;
                overlay.style.height = `${overlay.offsetHeight}px`;
                overlay.style.maxWidth = '100vw';
                overlay.style.maxHeight = '100vh';
                e.preventDefault();
                return;
            }

            const contentEl = document.getElementById(`${OVERLAY_ID}-content`);
            const isAltOrMeta = e.altKey || e.metaKey;
            const isPaddingClick = contentEl && (contentEl !== e.target && !contentEl.contains(e.target as Node));

            // Drag window if Alt is pressed or clicking the outer container padding
            if (isAltOrMeta || isPaddingClick) {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                origX = overlay.offsetLeft;
                origY = overlay.offsetTop;
                e.preventDefault();
            }
            // Otherwise, native drag-to-select text works without any interference
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

        // Positioning
        const defaultX = Math.max(8, window.innerWidth - (s.style_maxWidth || 380) - 12);
        const defaultY = Math.max(8, window.innerHeight - (s.style_maxHeight || 280) - 12);
        const posX = s.overlay_x >= 0 ? Math.min(s.overlay_x, window.innerWidth - 40) : defaultX;
        const posY = s.overlay_y >= 0 ? Math.min(s.overlay_y, window.innerHeight - 40) : defaultY;

        overlay.style.position = 'fixed';
        overlay.style.left = `${posX}px`;
        overlay.style.top = `${posY}px`;
        overlay.style.width = `${s.style_maxWidth}px`;
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
        overlay.style.resize = 'both';
        overlay.style.overflow = 'hidden';

        // Pure Minimalist Text Layout (No copy/x buttons, no hover highlight)
        overlay.innerHTML = `<div id="${OVERLAY_ID}-content"></div>`;

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
            sendResponse({ status: 'pong' });
            return false;
        }

        if (request.type === 'displayResult') {
            void showOverlay(request.text, request.status);
            sendResponse({ status: 'ok' });
        } else if (request.type === 'toggleOverlay') {
            toggleOverlay();
            sendResponse({ status: 'ok' });
        } else if (request.type === 'error') {
            void showOverlay(`Err: ${request.error}`, 'error');
            sendResponse({ status: 'ok' });
        } else if (request.type === 'clearOverlay') {
            const overlay = document.getElementById(OVERLAY_ID);
            if (overlay) overlay.style.display = 'none';
            sendResponse({ status: 'ok' });
        }

        return false;
    });

    // Live Settings Update
    chrome.storage.onChanged.addListener((changes, namespace) => {
        if (namespace === 'local') {
            const overlay = document.getElementById(OVERLAY_ID);
            if (overlay && overlay.style.display !== 'none') {
                const styleKeys = [
                    'style_fontSize',
                    'style_textColor',
                    'style_bgColor',
                    'style_bgOpacity',
                    'style_maxWidth',
                    'style_maxHeight',
                    'stealth_mode'
                ];
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