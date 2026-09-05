import {ARGUS_ICON_DATA_URL, COMMANDS} from './utils/constants';
import {createNotification, getActiveTab, injectContentScript, sendMessageToTab} from './utils/chrome-helpers';
import {captureVisibleTab} from './services/capture-service';
import {stateManager} from './services/state-manager';
import {sessionManager} from './services/session-manager';
import {queryWithContext} from './services/session-context';
import {type ChromeMessage, MessageFactory} from './types/messages';

import contentScriptPath from './content?script';

// Initialize state manager on service worker startup
stateManager
    .initialize()
    .then(() => console.log('[Argus] State manager initialized successfully'))
    .catch((e) => console.error('[Argus] Failed to initialize state manager:', e));

// Debounce & concurrency lock to prevent duplicate shortcuts and API calls
let isAnalyzing = false;
const lastActionTimes: Record<string, number> = {};

function isDebounced(action: string, cooldownMs = 700): boolean {
    const now = Date.now();
    const last = lastActionTimes[action] || 0;
    if (now - last < cooldownMs) {
        return true;
    }
    lastActionTimes[action] = now;
    return false;
}

// Handle keyboard shortcuts
chrome.commands.onCommand.addListener(async (command) => {
    try {
        if (isDebounced(command)) return;
        if (command === COMMANDS.TOGGLE_OVERLAY) {
            await handleToggleOverlay();
        } else if (command === COMMANDS.CAPTURE_AND_QUERY) {
            await handleCaptureAndQuery();
        } else if (command === COMMANDS.NEW_SESSION) {
            await handleNewSession();
        }
    } catch (error) {
        console.error('[Argus] Command execution failed:', error);
    }
});

// Handle direct message requests from content scripts (instant keyboard shortcut interception & follow-up questions)
chrome.runtime.onMessage.addListener((message: ChromeMessage, sender, sendResponse) => {
    if (message.type === 'REQUEST_CAPTURE') {
        if (!isDebounced('capture_and_query')) {
            void handleCaptureAndQuery();
        }
        sendResponse({status: 'ok'});
        return false;
    }
    if (message.type === 'REQUEST_TOGGLE_OVERLAY') {
        if (!isDebounced('toggle_overlay')) {
            void handleToggleOverlay();
        }
        sendResponse({status: 'ok'});
        return false;
    }
    if (message.type === 'REQUEST_NEW_SESSION') {
        if (!isDebounced('new_session')) {
            void handleNewSession();
        }
        sendResponse({status: 'ok'});
        return false;
    }
    if (message.type === 'REQUEST_CUSTOM_QUERY') {
        void handleCustomQuery(message.prompt, sender?.tab?.id);
        sendResponse({status: 'ok'});
        return false;
    }
    return false;
});

// Handle extension icon click
if (chrome.action?.onClicked) {
    chrome.action.onClicked.addListener(async (tab) => {
        if (!tab.id) return;
        try {
            await injectContentScript(tab.id, [contentScriptPath]);
            await sendMessageToTab(tab.id, MessageFactory.toggleOverlay());
        } catch {
            // If injection fails, open options page
            chrome.runtime.openOptionsPage();
        }
    });
}

async function showRestrictedPageNotification() {
    try {
        await createNotification('argus-restricted-error', {
            type: 'basic',
            iconUrl: ARGUS_ICON_DATA_URL,
            title: 'Argus Access Restricted',
            message: 'Cannot inject on this page. Extension access is restricted on chrome:// system pages, the Chrome Web Store, or local file schemes.'
        });
    } catch (err) {
        console.error('[Argus] Notification failed:', err);
    }
}

async function handleToggleOverlay() {
    const tab = await getActiveTab();
    if (!tab?.id) return;

    try {
        await injectContentScript(tab.id, [contentScriptPath]);
        await sendMessageToTab(tab.id, MessageFactory.toggleOverlay());
    } catch (error) {
        console.error('[Argus] Failed to toggle overlay:', error);
        await showRestrictedPageNotification();
    }
}

async function handleCaptureAndQuery() {
    if (isAnalyzing) {
        console.log('[Argus] Analysis already in progress, skipping duplicate.');
        return;
    }

    const tab = await getActiveTab();
    if (!tab?.id || !tab.windowId) return;

    try {
        await injectContentScript(tab.id, [contentScriptPath]);
    } catch (error) {
        console.error('[Argus] Failed to inject content script:', error);
        await showRestrictedPageNotification();
        return;
    }

    isAnalyzing = true;
    try {
        await sendMessageToTab(tab.id, MessageFactory.displayResult('Analyzing...', 'loading'));

        const sessionId = await sessionManager.getCurrentSession();
        const dataUrl = await captureVisibleTab(tab.windowId);

        if (!dataUrl) {
            await sendMessageToTab(tab.id, MessageFactory.error('Capture failed'));
            return;
        }

        const settings = await stateManager.getSettings();

        const onChunk = (chunk: string, fullText: string) => {
            if (tab.id) {
                void sendMessageToTab(tab.id, MessageFactory.displayStreamChunk(chunk, fullText)).catch(() => {
                });
            }
        };

        const response = await queryWithContext(dataUrl, settings, sessionId, undefined, onChunk);

        if (response.startsWith('Error:')) {
            await sendMessageToTab(tab.id, MessageFactory.error(response.replace(/^Error:\s*/, '')));
        } else {
            await sendMessageToTab(tab.id, MessageFactory.displayResult(response, 'success'));
        }
    } catch (e) {
        console.error('[Argus] Failed during capture and query:', e);
        try {
            await sendMessageToTab(tab.id, MessageFactory.error('Unexpected query error'));
        } catch {
        }
    } finally {
        isAnalyzing = false;
    }
}

async function handleCustomQuery(prompt: string, senderTabId?: number) {
    if (isAnalyzing) {
        console.log('[Argus] Query already in progress, skipping.');
        return;
    }

    const tab = senderTabId ? {id: senderTabId, windowId: (await getActiveTab())?.windowId} : await getActiveTab();
    if (!tab?.id) return;

    isAnalyzing = true;
    try {
        await sendMessageToTab(tab.id, MessageFactory.displayResult('Thinking...', 'loading'));

        const sessionId = await sessionManager.getCurrentSession();
        const settings = await stateManager.getSettings();

        // Capture current screen as visual context if available
        let dataUrl: string | null = null;
        if (tab.windowId) {
            dataUrl = await captureVisibleTab(tab.windowId);
        }

        const onChunk = (chunk: string, fullText: string) => {
            if (tab.id) {
                void sendMessageToTab(tab.id, MessageFactory.displayStreamChunk(chunk, fullText)).catch(() => {
                });
            }
        };

        const response = await queryWithContext(dataUrl, settings, sessionId, prompt, onChunk);

        if (response.startsWith('Error:')) {
            await sendMessageToTab(tab.id, MessageFactory.error(response.replace(/^Error:\s*/, '')));
        } else {
            await sendMessageToTab(tab.id, MessageFactory.displayResult(response, 'success'));
        }
    } catch (e) {
        console.error('[Argus] Failed custom query:', e);
        try {
            await sendMessageToTab(tab.id, MessageFactory.error('Failed to process question'));
        } catch {
        }
    } finally {
        isAnalyzing = false;
    }
}

async function handleNewSession() {
    const sessionId = await sessionManager.createSession();
    const tab = await getActiveTab();

    if (tab?.id) {
        try {
            await injectContentScript(tab.id, [contentScriptPath]);
            await sendMessageToTab(
                tab.id,
                MessageFactory.displayResult('New session.', 'info')
            );
        } catch {
            // Tab may be restricted or not receptive
        }
    }

    try {
        await createNotification('argus-session', {
            type: 'basic',
            iconUrl: ARGUS_ICON_DATA_URL,
            title: 'Argus - New Session',
            message: `Started a fresh conversation session (${sessionId.slice(-6)}).`
        });
    } catch (err) {
        console.error('[Argus] Notification failed:', err);
    }
}
