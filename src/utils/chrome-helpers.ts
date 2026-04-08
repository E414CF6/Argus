export async function getActiveTab() {
    const tabs = await chrome.tabs.query({active: true, currentWindow: true});
    return tabs[0] || null;
}

export async function injectContentScript(tabId: number, files: string[]): Promise<void> {
    try {
        await chrome.tabs.sendMessage(tabId, {type: 'PING'});
    } catch {
        await chrome.scripting.executeScript({target: {tabId}, files});
    }
}

export async function sendMessageToTab<T = unknown>(tabId: number, message: unknown): Promise<T> {
    return chrome.tabs.sendMessage(tabId, message) as Promise<T>;
}

export async function getStorageValues<T extends Record<string, unknown>>(keys: string | string[] | Record<string, unknown> | null): Promise<T> {
    return chrome.storage.local.get(keys as never) as Promise<T>;
}

export async function setStorageValues(items: Record<string, unknown>): Promise<void> {
    return chrome.storage.local.set(items);
}

export async function createNotification(id: string, options: chrome.notifications.NotificationCreateOptions): Promise<string> {
    return new Promise(resolve => chrome.notifications.create(id, options, resolve));
}
