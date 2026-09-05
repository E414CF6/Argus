import {DEFAULT_SETTINGS, type ExtensionSettings} from './types/settings';
import {type ChromeMessage, MessageFactory} from './types/messages';

if (typeof window.argusInjected === 'undefined') {
    window.argusInjected = true;

    const HOST_ID = 'argus-overlay-host';
    const OVERLAY_ID = 'argus-overlay';
    const UNBLOCK_STYLE_ID = 'argus-unblock-drag-style';

    let currentSettings: ExtensionSettings = {...DEFAULT_SETTINGS};
    let hostElement: HTMLElement | null = null;
    let shadowRoot: ShadowRoot | null = null;
    let latestRawText = '';

    function hexToRgba(hex: string, opacity: number): string {
        const cleanHex = (hex || '#000000').replace('#', '');
        const r = parseInt(cleanHex.slice(0, 2), 16) || 0;
        const g = parseInt(cleanHex.slice(2, 4), 16) || 0;
        const b = parseInt(cleanHex.slice(4, 6), 16) || 0;
        const alpha = Math.max(0, Math.min(1, opacity / 100));
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }

    function ensureShadowRoot(): ShadowRoot {
        if (!hostElement) {
            hostElement = document.getElementById(HOST_ID);
        }
        if (!hostElement) {
            hostElement = document.createElement('div');
            hostElement.id = HOST_ID;
            hostElement.style.all = 'initial';
            hostElement.style.position = 'fixed';
            hostElement.style.top = '0';
            hostElement.style.left = '0';
            hostElement.style.width = '0';
            hostElement.style.height = '0';
            hostElement.style.zIndex = '2147483647';
            hostElement.style.pointerEvents = 'none';
            (document.body || document.documentElement).appendChild(hostElement);
        }
        if (!shadowRoot) {
            shadowRoot = hostElement.shadowRoot || hostElement.attachShadow({mode: 'open'});
        }
        return shadowRoot;
    }

    async function loadSettings(): Promise<ExtensionSettings> {
        try {
            const data = await chrome.storage.local.get(DEFAULT_SETTINGS as unknown as Record<string, unknown>);
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
     * Non-destructive Unblock Engine: Restores text selection & copying without breaking
     * native HTML5 drag-and-drop or interactive web apps (Jira, Trello, Google Docs).
     */
    function applyPageDragUnblocker(enable = true): void {
        const existingStyle = document.getElementById(UNBLOCK_STYLE_ID);
        if (!enable) {
            if (existingStyle) existingStyle.remove();
            return;
        }

        if (!existingStyle) {
            const style = document.createElement('style');
            style.id = UNBLOCK_STYLE_ID;
            style.textContent = `
                body, p, span, h1, h2, h3, h4, h5, h6, td, th, li, code, pre, article, section, label, blockquote {
                    -webkit-user-select: text !important;
                    -moz-user-select: text !important;
                    -ms-user-select: text !important;
                    user-select: text !important;
                }
            `;
            (document.head || document.documentElement).appendChild(style);
        }

        try {
            document.onselectstart = null;
            document.oncontextmenu = null;
            if (document.body) {
                document.body.onselectstart = null;
                document.body.oncontextmenu = null;
            }
        } catch {
            // Ignore restricted document contexts
        }
    }

    // Allow user text selection even on pages with hostile traps
    window.addEventListener('selectstart', (e) => {
        if (!currentSettings.unblock_drag) return;
        const target = e.target as HTMLElement | null;
        if (target && target.style && target.style.userSelect === 'none') {
            target.style.userSelect = 'text';
        }
    }, true);

    // Guaranteed copy handler: Intervenes if a hostile script prevented default copy
    window.addEventListener('copy', (e) => {
        if (!currentSettings.unblock_drag) return;
        const selectedText = window.getSelection()?.toString();
        if (selectedText && selectedText.length > 0 && e.defaultPrevented) {
            if (e.clipboardData) {
                e.clipboardData.setData('text/plain', selectedText);
            }
        }
    }, false);

    /**
     * Robust Markdown Formatter: supports code blocks with copy buttons, bold, italic,
     * bullet lists (without pre-wrap gaps), headers, and links.
     */
    function formatMarkdown(raw: string): string {
        if (!raw) return '';

        // 1. Temporarily extract fenced code blocks to prevent nested formatting
        const codeBlocks: string[] = [];
        const placeholder = '___ARGUS_CODE_BLOCK_';

        let processed = raw.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (_match, lang, code) => {
            const idx = codeBlocks.length;
            const cleanLang = lang.trim() || 'code';
            const escapedCode = code
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');

            codeBlocks.push(`
                <div class="argus-code-box">
                    <div class="argus-code-bar">
                        <span class="argus-code-lang">${cleanLang}</span>
                        <button class="argus-code-copy-btn" data-code="${encodeURIComponent(code)}">Copy</button>
                    </div>
                    <pre class="argus-pre"><code>${escapedCode}</code></pre>
                </div>
            `);
            return `${placeholder}${idx}___`;
        });

        // 2. Escape general text
        processed = processed
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // 3. Inline formatting
        processed = processed.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        processed = processed.replace(/(?<!\*)\*([^*]+?)\*(?!\*)/g, '<em>$1</em>');
        processed = processed.replace(/`([^`]+)`/g, '<code class="argus-inline-code">$1</code>');

        // 4. Headers and Bullet lists
        const lines = processed.split('\n');
        const formattedLines = lines.map(line => {
            const trimmed = line.trim();
            if (trimmed.startsWith('### ')) {
                return `<div class="argus-h3">${trimmed.substring(4)}</div>`;
            }
            if (trimmed.startsWith('## ')) {
                return `<div class="argus-h2">${trimmed.substring(3)}</div>`;
            }
            if (trimmed.startsWith('# ')) {
                return `<div class="argus-h1">${trimmed.substring(2)}</div>`;
            }
            if (trimmed.startsWith('* ') || trimmed.startsWith('- ') || trimmed.startsWith('• ')) {
                const text = trimmed.replace(/^(\*|-|•)\s+/, '');
                return `<div class="argus-list-item"><span class="argus-bullet">•</span><span class="argus-list-text">${text}</span></div>`;
            }
            return line;
        });

        let result = formattedLines.join('\n');

        // 5. Restore code blocks
        result = result.replace(new RegExp(`${placeholder}(\\d+)___`, 'g'), (_match, idx) => {
            return codeBlocks[parseInt(idx, 10)] || '';
        });

        return result;
    }

    function ensureStylesInjected(root: ShadowRoot): void {
        if (root.getElementById(`${OVERLAY_ID}-style`)) return;

        const style = document.createElement('style');
        style.id = `${OVERLAY_ID}-style`;
        style.textContent = `
            * {
                box-sizing: border-box !important;
            }
            #${OVERLAY_ID} {
                box-sizing: border-box !important;
                font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                margin: 0 !important;
                padding: 6px 10px 10px 10px !important;
                display: flex !important;
                flex-direction: column !important;
                user-select: text !important;
                -webkit-user-select: text !important;
                box-shadow: 0 10px 35px rgba(0, 0, 0, 0.45) !important;
                border: 1px solid rgba(255, 255, 255, 0.1) !important;
                outline: none !important;
                position: fixed !important;
                pointer-events: auto !important;
                z-index: 2147483647 !important;
                transition: opacity 0.12s ease !important;
            }
            #${OVERLAY_ID}.argus-hidden,
            #${OVERLAY_ID}[hidden] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
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
                height: 18px !important;
                flex-shrink: 0 !important;
                margin-bottom: 4px !important;
                user-select: none !important;
                -webkit-user-select: none !important;
            }
            #${OVERLAY_ID}-drag-bar {
                height: 18px !important;
                flex: 1 !important;
                cursor: grab !important;
                display: flex !important;
                align-items: center !important;
                justify-content: flex-start !important;
                opacity: 0.35 !important;
                transition: opacity 0.15s ease !important;
                padding: 0 4px !important;
            }
            #${OVERLAY_ID}:hover #${OVERLAY_ID}-drag-bar {
                opacity: 0.8 !important;
            }
            #${OVERLAY_ID}-drag-bar::after {
                content: '' !important;
                width: 24px !important;
                height: 3px !important;
                background-color: currentColor !important;
                border-radius: 2px !important;
                opacity: 0.7 !important;
            }
            #${OVERLAY_ID}-drag-bar:active {
                cursor: grabbing !important;
            }
            .argus-btn-group {
                display: flex !important;
                align-items: center !important;
                gap: 4px !important;
            }
            .argus-icon-btn {
                width: 18px !important;
                height: 18px !important;
                cursor: pointer !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 12px !important;
                line-height: 1 !important;
                opacity: 0.35 !important;
                transition: opacity 0.15s ease, background 0.15s ease !important;
                border-radius: 4px !important;
                color: currentColor !important;
                background: transparent !important;
                border: none !important;
                padding: 0 !important;
            }
            #${OVERLAY_ID}:hover .argus-icon-btn {
                opacity: 0.65 !important;
            }
            .argus-icon-btn:hover {
                opacity: 1 !important;
                background: rgba(128, 128, 128, 0.25) !important;
            }
            #${OVERLAY_ID}-content {
                user-select: text !important;
                -webkit-user-select: text !important;
                cursor: text !important;
                white-space: pre-wrap !important;
                word-break: break-word !important;
                line-height: 1.45 !important;
                overflow-y: auto !important;
                flex: 1 !important;
                min-height: 0 !important;
                scrollbar-width: thin !important;
                scrollbar-color: rgba(128, 128, 128, 0.2) transparent !important;
                padding-right: 2px !important;
            }
            #${OVERLAY_ID}-content::-webkit-scrollbar {
                width: 4px !important;
            }
            #${OVERLAY_ID}-content::-webkit-scrollbar-thumb {
                background: rgba(128, 128, 128, 0.3) !important;
                border-radius: 2px !important;
            }
            /* Code block styling */
            .argus-code-box {
                background: rgba(0, 0, 0, 0.35) !important;
                border: 1px solid rgba(128, 128, 128, 0.25) !important;
                border-radius: 6px !important;
                margin: 6px 0 !important;
                overflow: hidden !important;
            }
            .argus-code-bar {
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                background: rgba(128, 128, 128, 0.12) !important;
                padding: 2px 8px !important;
                font-size: 0.72rem !important;
                font-family: monospace !important;
                opacity: 0.75 !important;
            }
            .argus-code-copy-btn {
                background: transparent !important;
                border: none !important;
                color: currentColor !important;
                cursor: pointer !important;
                font-size: 0.7rem !important;
                padding: 1px 5px !important;
                border-radius: 3px !important;
                opacity: 0.8 !important;
            }
            .argus-code-copy-btn:hover {
                background: rgba(255, 255, 255, 0.15) !important;
                opacity: 1 !important;
            }
            .argus-pre {
                margin: 0 !important;
                padding: 6px 8px !important;
                overflow-x: auto !important;
                font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace !important;
                font-size: 0.88em !important;
                line-height: 1.35 !important;
            }
            .argus-inline-code {
                background: rgba(128, 128, 128, 0.2) !important;
                padding: 1px 4px !important;
                border-radius: 3px !important;
                font-family: monospace !important;
                font-size: 0.9em !important;
            }
            .argus-list-item {
                display: flex !important;
                align-items: flex-start !important;
                margin: 2px 0 !important;
                line-height: 1.4 !important;
            }
            .argus-bullet {
                margin-right: 6px !important;
                opacity: 0.6 !important;
            }
            .argus-h1, .argus-h2, .argus-h3 {
                font-weight: 700 !important;
                margin: 6px 0 2px 0 !important;
            }
            .argus-h1 { font-size: 1.15em !important; }
            .argus-h2 { font-size: 1.05em !important; }
            .argus-h3 { font-size: 0.95em !important; opacity: 0.9 !important; }

            /* Interactive Follow-up Input Bar */
            #${OVERLAY_ID}-prompt-bar {
                display: flex !important;
                align-items: center !important;
                gap: 6px !important;
                margin-top: 6px !important;
                padding-top: 4px !important;
                border-top: 1px solid rgba(128, 128, 128, 0.15) !important;
                flex-shrink: 0 !important;
            }
            #${OVERLAY_ID}-prompt-input {
                flex: 1 !important;
                background: rgba(128, 128, 128, 0.12) !important;
                border: 1px solid rgba(128, 128, 128, 0.2) !important;
                border-radius: 4px !important;
                padding: 4px 8px !important;
                color: inherit !important;
                font-size: 0.82em !important;
                outline: none !important;
                transition: border-color 0.15s ease !important;
            }
            #${OVERLAY_ID}-prompt-input:focus {
                border-color: rgba(99, 102, 241, 0.7) !important;
                background: rgba(128, 128, 128, 0.18) !important;
            }
            #${OVERLAY_ID}-prompt-input::placeholder {
                color: currentColor !important;
                opacity: 0.4 !important;
            }

            /* Resizer Corner */
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
                opacity: 0.3 !important;
                transition: opacity 0.15s ease !important;
            }
            #${OVERLAY_ID}:hover #${OVERLAY_ID}-resizer,
            #${OVERLAY_ID}-resizer:hover {
                opacity: 0.85 !important;
            }
            .argus-stealth-dot {
                display: inline-block !important;
                width: 5px !important;
                height: 5px !important;
                background-color: currentColor !important;
                border-radius: 50% !important;
                opacity: 0.5 !important;
                animation: argus-pulse 1.2s infinite ease-in-out !important;
                margin-right: 6px !important;
            }
            @keyframes argus-pulse {
                0%, 100% { opacity: 0.2; transform: scale(0.9); }
                50% { opacity: 0.8; transform: scale(1.1); }
            }
        `;
        root.appendChild(style);
    }

    function makeDraggable(overlay: HTMLElement, root: ShadowRoot): void {
        let isDragging = false;
        let isResizing = false;
        let startX = 0, startY = 0;
        let origX = 0, origY = 0;
        let startWidth = 0, startHeight = 0;

        overlay.addEventListener('mousedown', (e) => {
            const target = e.target as HTMLElement | null;
            if (!target) return;

            // Ignore clicks on buttons or input bar
            if (target.closest('.argus-icon-btn') || target.closest('#' + OVERLAY_ID + '-prompt-bar')) {
                return;
            }

            const resizer = root.getElementById(`${OVERLAY_ID}-resizer`);
            const dragBar = root.getElementById(`${OVERLAY_ID}-drag-bar`);
            const rect = overlay.getBoundingClientRect();

            // 1. Resize Handle (bottom-right)
            const isResizerClick = resizer && (resizer === target || resizer.contains(target));
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

            // 2. Drag & Reposition
            const isDragBar = dragBar && (dragBar === target || dragBar.contains(target));
            const isModifierPressed = e.altKey || e.shiftKey || e.metaKey || e.ctrlKey;
            const contentEl = root.getElementById(`${OVERLAY_ID}-content`);
            const isOutsideText = contentEl && (contentEl !== target && !contentEl.contains(target));

            if (isDragBar || isModifierPressed || isOutsideText) {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                origX = overlay.offsetLeft;
                origY = overlay.offsetTop;
                e.preventDefault();
                e.stopPropagation();
            }
        });

        window.addEventListener('mousemove', (e) => {
            if (isResizing) {
                const newWidth = Math.max(160, Math.min(window.innerWidth - origX - 10, startWidth + (e.clientX - startX)));
                const newHeight = Math.max(80, Math.min(window.innerHeight - origY - 10, startHeight + (e.clientY - startY)));
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
                void saveSize(overlay.offsetWidth, overlay.offsetHeight);
            } else if (isDragging) {
                isDragging = false;
                void savePosition(overlay.offsetLeft, overlay.offsetTop);
            }
        }, {capture: true});
    }

    function attachHeaderAndInputEvents(root: ShadowRoot): void {
        const closeBtn = root.getElementById(`${OVERLAY_ID}-close-btn`);
        if (closeBtn) {
            closeBtn.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                hideOverlay();
            };
        }

        const copyBtn = root.getElementById(`${OVERLAY_ID}-copy-btn`);
        if (copyBtn) {
            copyBtn.onclick = async (e) => {
                e.preventDefault();
                e.stopPropagation();
                if (latestRawText) {
                    try {
                        await navigator.clipboard.writeText(latestRawText);
                        const origText = copyBtn.textContent;
                        copyBtn.textContent = '✓';
                        setTimeout(() => {
                            copyBtn.textContent = origText;
                        }, 1200);
                    } catch {
                        // Clipboard fallback
                    }
                }
            };
        }

        const clearBtn = root.getElementById(`${OVERLAY_ID}-clear-btn`);
        if (clearBtn) {
            clearBtn.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                const contentEl = root.getElementById(`${OVERLAY_ID}-content`);
                if (contentEl) {
                    contentEl.innerHTML = 'Ready. Press shortcut to capture.';
                    latestRawText = '';
                }
            };
        }

        const dragBar = root.getElementById(`${OVERLAY_ID}-drag-bar`);
        if (dragBar) {
            dragBar.ondblclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                hideOverlay();
            };
        }

        // Code block copy buttons
        const contentEl = root.getElementById(`${OVERLAY_ID}-content`);
        if (contentEl) {
            contentEl.onclick = async (e) => {
                const target = e.target as HTMLElement | null;
                if (target && target.classList.contains('argus-code-copy-btn')) {
                    const encodedCode = target.getAttribute('data-code');
                    if (encodedCode) {
                        try {
                            const code = decodeURIComponent(encodedCode);
                            await navigator.clipboard.writeText(code);
                            target.textContent = 'Copied!';
                            setTimeout(() => {
                                target.textContent = 'Copy';
                            }, 1500);
                        } catch {
                            // Copy failure fallback
                        }
                    }
                }
            };
        }

        // Prompt input: Enter triggers custom follow-up query
        const promptInput = root.getElementById(`${OVERLAY_ID}-prompt-input`) as HTMLInputElement | null;
        if (promptInput) {
            promptInput.onkeydown = (e) => {
                e.stopPropagation();
                if (e.key === 'Enter') {
                    const text = promptInput.value.trim();
                    if (text.length > 0) {
                        promptInput.value = '';
                        void showOverlay('Thinking...', 'loading');
                        try {
                            void chrome.runtime.sendMessage(MessageFactory.requestCustomQuery(text));
                        } catch (err) {
                            console.error('[Argus] Failed to send query:', err);
                        }
                    }
                } else if (e.key === 'Escape') {
                    promptInput.blur();
                }
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
        return true;
    }

    function hideOverlay(): void {
        const root = ensureShadowRoot();
        const overlay = root.getElementById(OVERLAY_ID);
        if (overlay) {
            overlay.classList.add('argus-hidden');
            overlay.setAttribute('hidden', '');
            overlay.style.setProperty('display', 'none', 'important');
        }
    }

    function showOverlayElement(overlay: HTMLElement): void {
        overlay.classList.remove('argus-hidden');
        overlay.removeAttribute('hidden');
        overlay.style.setProperty('display', 'flex', 'important');
    }

    async function showOverlay(text: string, status?: 'loading' | 'success' | 'error' | 'info'): Promise<void> {
        const root = ensureShadowRoot();
        ensureStylesInjected(root);
        const s = await loadSettings();

        latestRawText = text;

        let overlay = root.getElementById(OVERLAY_ID);
        const isNew = !overlay;

        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = OVERLAY_ID;
            root.appendChild(overlay);
        }

        showOverlayElement(overlay);

        // Viewport clamping & positioning
        const defaultX = Math.max(8, window.innerWidth - (s.style_maxWidth || 380) - 16);
        const defaultY = Math.max(8, window.innerHeight - (s.style_maxHeight || 280) - 16);
        const posX = s.overlay_x >= 0 ? Math.min(s.overlay_x, window.innerWidth - 60) : defaultX;
        const posY = s.overlay_y >= 0 ? Math.min(s.overlay_y, window.innerHeight - 60) : defaultY;

        overlay.style.left = `${posX}px`;
        overlay.style.top = `${posY}px`;
        overlay.style.width = `${s.style_maxWidth}px`;
        overlay.style.height = `${s.style_maxHeight}px`;
        overlay.style.maxWidth = `${s.style_maxWidth}px`;
        overlay.style.maxHeight = `${s.style_maxHeight}px`;
        overlay.style.background = hexToRgba(s.style_bgColor, s.style_bgOpacity);
        overlay.style.color = s.style_textColor;
        overlay.style.fontSize = `${s.style_fontSize}px`;
        overlay.style.borderRadius = s.style_bgOpacity > 0 ? '6px' : '0px';

        // Set structure only if new or DOM reset needed
        if (isNew || !root.getElementById(`${OVERLAY_ID}-content`)) {
            overlay.innerHTML = `
                <div id="${OVERLAY_ID}-header">
                    <div id="${OVERLAY_ID}-drag-bar" title="Drag to move (Double-click to hide)"></div>
                    <div class="argus-btn-group">
                        <button id="${OVERLAY_ID}-copy-btn" class="argus-icon-btn" title="Copy text (One-click)">📋</button>
                        <button id="${OVERLAY_ID}-clear-btn" class="argus-icon-btn" title="Clear overlay">🧹</button>
                        <button id="${OVERLAY_ID}-close-btn" class="argus-icon-btn" title="Close (Esc)">×</button>
                    </div>
                </div>
                <div id="${OVERLAY_ID}-content"></div>
                <div id="${OVERLAY_ID}-prompt-bar">
                    <input id="${OVERLAY_ID}-prompt-input" type="text" placeholder="Ask follow-up question (Enter)..." />
                </div>
                <div id="${OVERLAY_ID}-resizer" title="Drag corner to resize"></div>
            `;
            attachHeaderAndInputEvents(root);
            makeDraggable(overlay, root);
        }

        const contentEl = root.getElementById(`${OVERLAY_ID}-content`)!;

        if (status === 'loading') {
            contentEl.innerHTML = `<span class="argus-stealth-dot"></span>${formatMarkdown(text)}`;
        } else {
            contentEl.innerHTML = formatMarkdown(text);
        }
    }

    function updateStreamingText(fullText: string): void {
        const root = ensureShadowRoot();
        const overlay = root.getElementById(OVERLAY_ID);
        if (!overlay || !isOverlayVisible(overlay)) {
            void showOverlay(fullText, 'success');
            return;
        }

        latestRawText = fullText;
        const contentEl = root.getElementById(`${OVERLAY_ID}-content`);
        if (contentEl) {
            contentEl.innerHTML = formatMarkdown(fullText);
            contentEl.scrollTop = contentEl.scrollHeight;
        }
    }

    function toggleOverlay(): void {
        const root = ensureShadowRoot();
        const overlay = root.getElementById(OVERLAY_ID);
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

    // Dismiss with Escape key or keyboard shortcuts
    window.addEventListener('keydown', (e) => {
        const root = ensureShadowRoot();
        const overlay = root.getElementById(OVERLAY_ID);
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

        // 2. In-page fallback shortcut for toggle: Cmd/Ctrl+Shift+X or Cmd/Ctrl+Shift+D
        if ((isShift && isMod && (key === 'x' || key === 'd')) || (isShift && e.altKey && key === 'd')) {
            toggleOverlay();
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        // 3. In-page fallback shortcut for capture: Cmd/Ctrl+Shift+E
        if (isShift && isMod && key === 'e') {
            try {
                void chrome.runtime.sendMessage(MessageFactory.requestCapture());
            } catch {
                // Ignore if runtime unavailable
            }
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        // 4. In-page fallback shortcut for new session: Cmd/Ctrl+Shift+N
        if (isShift && isMod && key === 'n') {
            try {
                void chrome.runtime.sendMessage(MessageFactory.requestNewSession());
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

        if (request.type === 'displayStreamChunk') {
            updateStreamingText(request.fullText);
            sendResponse({status: 'ok'});
        } else if (request.type === 'displayResult') {
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

            const root = ensureShadowRoot();
            const overlay = root.getElementById(OVERLAY_ID);
            if (overlay && isOverlayVisible(overlay)) {
                const styleKeys = ['style_fontSize', 'style_textColor', 'style_bgColor', 'style_bgOpacity', 'style_maxWidth', 'style_maxHeight', 'stealth_mode'];
                const hasStyleChange = Object.keys(changes).some(key => styleKeys.includes(key));
                if (hasStyleChange) {
                    const contentEl = root.getElementById(`${OVERLAY_ID}-content`);
                    if (contentEl) {
                        void showOverlay(latestRawText || contentEl.innerText || '');
                    }
                }
            }
        }
    });

    // Initial load
    void loadSettings();
}