interface OverlaySettings {
    style_fontSize: number;
    style_textColor: string;
    style_bgColor: string;
    style_bgOpacity: number;
    style_position: string;
}

const DEFAULT_STYLE: OverlaySettings = {
    style_fontSize: 13,
    style_textColor: '#e8e8e8',
    style_bgColor: '#1e1e1e',
    style_bgOpacity: 95,
    style_position: 'bottom-right',
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

    function getPositionStyle(pos: string): string {
        switch (pos) {
            case 'top-left':
                return 'top: 20px; left: 20px;';
            case 'top-right':
                return 'top: 20px; right: 20px;';
            case 'bottom-left':
                return 'bottom: 20px; left: 20px;';
            default:
                return 'bottom: 20px; right: 20px;';
        }
    }

    async function loadSettings(): Promise<OverlaySettings> {
        return chrome.storage.local.get(DEFAULT_STYLE) as Promise<OverlaySettings>;
    }

    async function showOverlay(text: string) {
        const s = await loadSettings();
        let overlay = document.getElementById(OVERLAY_ID);

        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = OVERLAY_ID;
            document.body.appendChild(overlay);
        }

        overlay.textContent = text;
        overlay.style.cssText = `
            position: fixed;
            ${getPositionStyle(s.style_position)}
            max-width: 400px;
            max-height: 300px;
            padding: 12px 16px;
            background: ${hexToRgba(s.style_bgColor, s.style_bgOpacity)};
            color: ${s.style_textColor};
            font: ${s.style_fontSize}px/1.5 system-ui, sans-serif;
            border-radius: 8px;
            z-index: 999999999;
            white-space: pre-wrap;
            overflow-y: auto;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        `;
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