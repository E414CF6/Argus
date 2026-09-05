/**
 * Session context - manages conversation history for API requests
 */

import {type ConversationMessage, sessionManager} from './session-manager';
import {queryGemini} from './gemini-adapter';
import type {ExtensionSettings} from '../types/settings';

export async function queryWithContext(
    dataUrl: string | null,
    settings: ExtensionSettings,
    sessionId: string | null,
    customPrompt?: string,
    onChunk?: (chunk: string, fullText: string) => void
): Promise<string> {
    let history: ConversationMessage[] = [];

    if (sessionId) {
        try {
            history = await sessionManager.getHistory(sessionId);
        } catch (error) {
            console.error('[Session Context] Failed to get history:', error);
        }
    }

    const effectivePrompt = customPrompt || settings.gemini_prompt;
    const response = await queryGemini(dataUrl, settings, history, effectivePrompt, onChunk);

    // Save only valid responses to conversation history (without huge image blob to prevent DB bloat)
    if (sessionId && !response.startsWith('Error:')) {
        try {
            await sessionManager.addMessage(sessionId, 'user', effectivePrompt);
            await sessionManager.addMessage(sessionId, 'assistant', response);
        } catch (error) {
            console.error('[Session Context] Failed to save history:', error);
        }
    }

    return response;
}
