// Commands from manifest
export const COMMANDS = {
    CAPTURE_AND_QUERY: 'capture_and_query', TOGGLE_OVERLAY: 'toggle_overlay', NEW_SESSION: 'new_session',
} as const;

// 128x128 Crisp SVG/PNG Data URI for notifications and UI (100% offline & CORS-safe)
export const ARGUS_ICON_DATA_URL = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" height="128"><circle cx="64" cy="64" r="58" fill="%236366f1"/><circle cx="64" cy="64" r="46" fill="%2318181b"/><circle cx="64" cy="64" r="24" fill="%236366f1"/><circle cx="64" cy="64" r="12" fill="%23ffffff"/></svg>';
