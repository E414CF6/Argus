export async function captureVisibleTab(windowId: number): Promise<string | null> {
    try {
        return await chrome.tabs.captureVisibleTab(windowId, {format: "jpeg"});
    } catch {
        return null;
    }
}
