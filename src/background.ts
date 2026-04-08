import {COMMANDS} from './utils/constants';
import {createNotification, getActiveTab, injectContentScript, sendMessageToTab} from './utils/chrome-helpers';
import {captureVisibleTab} from './services/capture-service';
import {stateManager} from './services/state-manager';
import {sessionManager} from './services/session-manager';
import {queryWithContext} from './services/session-context';
import {MessageFactory} from './types/messages';

import contentScriptPath from './content?script';

stateManager.initialize().then(r => console.log('[Argus] State manager initialized:', r)).catch(e => console.error('[Argus] Failed to initialize state manager:', e));

chrome.commands.onCommand.addListener(async (command) => {
    try {
        if (command === COMMANDS.TOGGLE_OVERLAY) {
            await handleToggleOverlay();
        } else if (command === COMMANDS.CAPTURE_AND_QUERY) {
            await handleCaptureAndQuery();
        } else if (command === COMMANDS.NEW_SESSION) {
            await handleNewSession();
        }
    } catch (error) {
        console.error('[Argus] Command failed:', error);
    }
});

async function handleToggleOverlay() {
    const tab = await getActiveTab();
    if (!tab?.id) return;
    await injectContentScript(tab.id, [contentScriptPath]);
    await sendMessageToTab(tab.id, MessageFactory.toggleOverlay());
}

async function handleCaptureAndQuery() {
    const tab = await getActiveTab();
    if (!tab?.id || !tab.windowId) return;

    await injectContentScript(tab.id, [contentScriptPath]);

    const sessionId = await sessionManager.getCurrentSession();
    const dataUrl = await captureVisibleTab(tab.windowId);

    if (!dataUrl) {
        await sendMessageToTab(tab.id, MessageFactory.error('Capture failed'));
        return;
    }

    await sendMessageToTab(tab.id, MessageFactory.displayResult('Querying AI...'));

    const settings = await stateManager.getSettings();
    const response = await queryWithContext(dataUrl, settings, sessionId);

    await sendMessageToTab(tab.id, MessageFactory.displayResult(response));
}

async function handleNewSession() {
    await sessionManager.createSession();
    await createNotification('argus-session', {
        type: 'basic',
        iconUrl: 'https://www.gstatic.com/lamda/images/gemini_sparkle_4g_512_lt_f94943af3be039176192d.png',
        title: 'New Session',
        message: 'Started new conversation'
    });
}
