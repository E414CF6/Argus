import {ARGUS_ICON_DATA_URL} from './constants';

export async function getActiveTab(): Promise<chrome.tabs.Tab | null> {
    try {
        const tabs = await chrome.tabs.query({active: true, currentWindow: true});
        return tabs[0] || null;
    } catch (err) {
        console.error('[ChromeHelpers] Failed to get active tab:', err);
        return null;
    }
}

/**
 * Injects content script and performs a bidirectional handshake to guarantee
 * that the content script listener is active and ready to receive messages.
 */
export async function injectContentScript(tabId: number, files: string[]): Promise<void> {
    // 1. Check if already alive
    try {
        const response = await chrome.tabs.sendMessage(tabId, {type: 'PING'});
        if (response?.status === 'pong') {
            return;
        }
    } catch {
        // Not active yet, proceed to inject
    }

    // 2. Inject script
    await chrome.scripting.executeScript({target: {tabId}, files});

    // 3. Poll for readiness handshake (up to 10 attempts, ~600ms max)
    for (let attempt = 0; attempt < 10; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 40 + attempt * 20));
        try {
            const response = await chrome.tabs.sendMessage(tabId, {type: 'PING'});
            if (response?.status === 'pong') {
                return;
            }
        } catch {
            // Still loading, retry next iteration
        }
    }

    throw new Error('Content script injection timed out: receiving end did not respond.');
}

/**
 * Sends a message to the tab with automatic retry on transient connection lags.
 */
export async function sendMessageToTab<T = unknown>(
    tabId: number,
    message: unknown,
    maxRetries = 3
): Promise<T> {
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return (await chrome.tabs.sendMessage(tabId, message)) as T;
        } catch (error) {
            const msg = error instanceof Error ? error.message : String(error);
            const isConnectionError =
                msg.includes('Receiving end does not exist') ||
                msg.includes('Could not establish connection');

            if (isConnectionError && attempt < maxRetries) {
                await new Promise((resolve) => setTimeout(resolve, 60 * (attempt + 1)));
                continue;
            }
            throw error;
        }
    }
    throw new Error(`Failed to send message to tab ${tabId}`);
}

export async function getStorageValues<T extends object>(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    keys: string | string[] | Record<string, any> | null
): Promise<T> {
    return chrome.storage.local.get(keys as never) as Promise<T>;
}

export async function setStorageValues<T extends object>(items: T): Promise<void> {
    return chrome.storage.local.set(items as Record<string, unknown>);
}

export async function createNotification(
    id: string,
    options: chrome.notifications.NotificationCreateOptions
): Promise<string> {
    const safeOptions: chrome.notifications.NotificationCreateOptions = {
        ...options,
        iconUrl: options.iconUrl && !options.iconUrl.startsWith('http') ? options.iconUrl : ARGUS_ICON_DATA_URL
    };

    return new Promise((resolve) => {
        chrome.notifications.create(id, safeOptions, (notificationId) => {
            if (chrome.runtime.lastError) {
                console.warn('[ChromeHelpers] Notification lastError:', chrome.runtime.lastError.message);
            }
            resolve(notificationId || id);
        });
    });
}
