/**
 * Gemini API adapter using official SDK
 */

import {GoogleGenAI} from '@google/genai';
import {handleApiError} from './error-handler';
import type {ExtensionSettings} from './state-manager';
import type {ConversationMessage} from './session-manager'; // 90 seconds

let aiInstance: GoogleGenAI | null = null;

function getAI(apiKey: string): GoogleGenAI {
    if (!aiInstance || (aiInstance as any).apiKey !== apiKey) {
        aiInstance = new GoogleGenAI({apiKey});
    }
    return aiInstance;
}

export interface ModelInfo {
    id: string;
    name: string;
    description?: string;
}

export async function listModels(apiKey: string): Promise<ModelInfo[]> {
    if (!apiKey) return [];

    try {
        const ai = getAI(apiKey);
        const pager = await ai.models.list();
        const models: ModelInfo[] = [];

        for await (const model of pager) {
            // Filter for generateContent capable models (gemini models)
            if (model.name?.includes('gemini') && !model.name?.includes('embedding') && !model.name?.includes('aqa')) {
                models.push({
                    id: model.name?.replace('models/', '') || '',
                    name: model.displayName || model.name || '',
                    description: model.description
                });
            }
        }

        // Sort: newer models first
        return models.sort((a, b) => {
            const aVer = a.id.match(/(\d+\.?\d*)/)?.[1] || '0';
            const bVer = b.id.match(/(\d+\.?\d*)/)?.[1] || '0';
            return parseFloat(bVer) - parseFloat(aVer);
        });
    } catch (error) {
        console.error('Failed to list models:', error);
        return [];
    }
}

export async function queryGemini(dataUrl: string, settings: ExtensionSettings, history: ConversationMessage[] = []): Promise<string> {
    const {gemini_apiKey, gemini_model, gemini_prompt, gemini_timeout} = settings;

    if (!gemini_apiKey) {
        throw new Error('API key not configured. Please set it in settings.');
    }

    try {
        const ai = getAI(gemini_apiKey);
        const parts: Array<{ text?: string; inlineData?: { mimeType: string; data: string } }> = [];

        // Add recent history context (last 6 messages)
        if (history.length > 0) {
            const recent = history.slice(-6);
            const ctx = recent.map(e => `${e.role === 'user' ? 'User' : 'AI'}: ${e.content}`).join('\n\n');
            parts.push({text: `Previous conversation:\n${ctx}\n\nNow analyze this screenshot:`});
        }

        // Add image
        const [mimeTypePart, base64Data] = dataUrl.split(';base64,');
        parts.push({
            inlineData: {
                mimeType: mimeTypePart.replace('data:', ''), data: base64Data
            }
        });

        // Add prompt
        parts.push({text: gemini_prompt});

        const timeoutMs = (gemini_timeout || 90) * 1000;
        const response = await Promise.race([ai.models.generateContent({
            model: gemini_model,
            contents: parts
        }), new Promise<never>((_, reject) => setTimeout(() => reject(new Error('Request timed out')), timeoutMs))]);

        return response.text || 'Empty response from API';
    } catch (error) {
        return await handleApiError(error);
    }
}
