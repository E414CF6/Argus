/**
 * Session context - manages conversation history for API requests
 */

import {type ConversationMessage, sessionManager} from './session-manager';
import {queryGemini} from './gemini-adapter';
import type {ExtensionSettings} from './state-manager';

export async function queryWithContext(dataUrl: string, settings: ExtensionSettings, sessionId: string | null): Promise<string> {
    let history: ConversationMessage[] = [];

    if (sessionId) {
        try {
            history = await sessionManager.getHistory(sessionId);
        } catch (error) {
            console.error('[Session Context] Failed to get history:', error);
        }
    }

    const response = await queryGemini(dataUrl, settings, history);

    if (sessionId) {
        try {
            await sessionManager.addMessage(sessionId, 'user', settings.gemini_prompt, dataUrl);
            await sessionManager.addMessage(sessionId, 'assistant', response);
        } catch (error) {
            console.error('[Session Context] Failed to save history:', error);
        }
    }

    return response;
}
