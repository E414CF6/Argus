interface OverlaySettings {
    style_fontSize: number;
    style_textColor: string;
    style_bgColor: string;
    style_bgOpacity: number;
    style_maxWidth: number;
    style_maxHeight: number;
    overlay_x: number;
    overlay_y: number;
}

const DEFAULT_STYLE: OverlaySettings = {
    style_fontSize: 13,
    style_textColor: '#e8e8e8',
    style_bgColor: '#1e1e1e',
    style_bgOpacity: 95,
    style_maxWidth: 400,
    style_maxHeight: 48,
    overlay_x: -1,
    overlay_y: -1,
};

if (typeof window.argusInjected === 'undefined') {
    window.argusInjected = true;

    const OVERLAY_ID = 'argus-overlay';

    function hexToRgba(hex: string, opacity: number): string {
        const r = parseInt(hex.slice(1, 3), 16);
        const g = parseInt(hex.slice(3, 5), 16);
        const b = parseInt(hex.slice(5, 7), 16);
        return `rgba(${r}, ${g}, ${b}, ${opacity / 100})`;
    }

    async function loadSettings(): Promise<OverlaySettings> {
        return chrome.storage.local.get(DEFAULT_STYLE) as Promise<OverlaySettings>;
    }

    async function savePosition(x: number, y: number) {
        await chrome.storage.local.set({overlay_x: x, overlay_y: y});
    }

    async function saveSize(width: number, height: number) {
        await chrome.storage.local.set({style_maxWidth: width, style_maxHeight: height});
    }

    function makeDraggable(overlay: HTMLElement) {
        let isDragging = false;
        let isResizing = false;
        let startX = 0, startY = 0;
        let origX = 0, origY = 0;

        overlay.addEventListener('mousedown', (e) => {
            const rect = overlay.getBoundingClientRect();
            // Check if click is near bottom-right corner (native resize handle)
            if (e.clientX > rect.right - 16 && e.clientY > rect.bottom - 16) {
                isResizing = true;
                // Capture current size so it doesn't jump when we lift max constraints
                overlay.style.width = `${overlay.offsetWidth}px`;
                overlay.style.height = `${overlay.offsetHeight}px`;
                // Temporarily lift max constraints to allow native resizing upwards
                overlay.style.maxWidth = '100vw';
                overlay.style.maxHeight = '100vh';
                return;
            }

            // Only drag if clicking on the overlay itself (padding area)
            // or if the user is holding Alt key (force drag)
            if (e.target !== overlay && !e.altKey) return;

            isDragging = true;
            startX = e.clientX;
            startY = e.clientY;
            origX = overlay.offsetLeft;
            origY = overlay.offsetTop;
            overlay.style.cursor = 'grabbing';
            e.preventDefault();
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
                // Save new dimensions and restore constraints
                const newWidth = overlay.offsetWidth;
                const newHeight = overlay.offsetHeight;
                overlay.style.width = '';
                overlay.style.height = '';
                overlay.style.maxWidth = `${newWidth}px`;
                overlay.style.maxHeight = `${newHeight}px`;
                saveSize(newWidth, newHeight);
            } else if (isDragging) {
                isDragging = false;
                overlay.style.cursor = 'grab';
                savePosition(overlay.offsetLeft, overlay.offsetTop);
            }
        });
    }

    async function showOverlay(text: string) {
        const s = await loadSettings();
        let overlay = document.getElementById(OVERLAY_ID) as HTMLElement | null;
        const isNew = !overlay;

        if (!document.getElementById(`${OVERLAY_ID}-style`)) {
            const style = document.createElement('style');
            style.id = `${OVERLAY_ID}-style`;
            style.textContent = `
                #${OVERLAY_ID}::-webkit-scrollbar {
                    display: none;
                }
                #${OVERLAY_ID}::-webkit-resizer {
                    background-color: transparent;
                }
            `;
            document.head.appendChild(style);
        }

        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = OVERLAY_ID;
            document.body.appendChild(overlay);
        }

        overlay.innerHTML = '';
        const content = document.createElement('div');
        content.id = `${OVERLAY_ID}-content`;
        content.style.userSelect = 'text';
        content.style.cursor = 'text';
        content.style.whiteSpace = 'pre-wrap';
        content.textContent = text;
        overlay.appendChild(content);

        // Default position: bottom-right with 20px margin
        const defaultX = window.innerWidth - 420;
        const defaultY = window.innerHeight - 320;
        const posX = s.overlay_x >= 0 ? s.overlay_x : defaultX;
        const posY = s.overlay_y >= 0 ? s.overlay_y : defaultY;

        overlay.style.cssText = `
            position: fixed;
            left: ${posX}px;
            top: ${posY}px;
            max-width: ${s.style_maxWidth}px;
            max-height: ${s.style_maxHeight}px;
            padding: 12px 16px;
            background: ${hexToRgba(s.style_bgColor, s.style_bgOpacity)};
            color: ${s.style_textColor};
            font: ${s.style_fontSize}px/1.5 system-ui, sans-serif;
            border-radius: 8px;
            z-index: 999999999;
            overflow: auto;
            resize: both;
            scrollbar-width: none;
            -ms-overflow-style: none;
            cursor: grab;
            user-select: text;
        `;

        if (isNew) {
            makeDraggable(overlay);
        }
    }

    function toggleOverlay() {
        const overlay = document.getElementById(OVERLAY_ID);
        if (!overlay) {
            showOverlay('Argus ready. Press shortcut to capture.');
            return;
        }
        overlay.style.display = overlay.style.display === 'none' ? 'block' : 'none';
    }

    chrome.runtime.onMessage.addListener((request, _sender, sendResponse) => {
        if (request.type === 'PING') {
            sendResponse({status: 'pong'});
            return false;
        }
        if (request.type === 'displayResult') {
            showOverlay(request.text);
            sendResponse({status: 'ok'});
        } else if (request.type === 'toggleOverlay') {
            toggleOverlay();
            sendResponse({status: 'ok'});
        } else if (request.type === 'error') {
            showOverlay(`❌ ${request.error}`);
            sendResponse({status: 'ok'});
        }
        return false;
    });
}