/**
 * Gemini API adapter using official @google/genai SDK
 */

import {GoogleGenAI} from '@google/genai';
import {handleApiError} from './error-handler';
import {type ExtensionSettings, FALLBACK_MODELS} from '../types/settings';
import type {ConversationMessage} from './session-manager';

let aiInstance: GoogleGenAI | null = null;
let activeApiKey: string | null = null;

export function getAI(apiKey: string): GoogleGenAI {
    if (!aiInstance || activeApiKey !== apiKey) {
        aiInstance = new GoogleGenAI({apiKey});
        activeApiKey = apiKey;
    }
    return aiInstance;
}

export interface ModelInfo {
    id: string;
    name: string;
    description?: string;
}

/**
 * Lists available Gemini models capable of visual/content generation.
 */
export async function listModels(apiKey: string): Promise<ModelInfo[]> {
    if (!apiKey || apiKey.trim().length < 10) return FALLBACK_MODELS;

    try {
        const ai = getAI(apiKey);
        const pager = await ai.models.list();
        const models: ModelInfo[] = [];

        for await (const model of pager) {
            const modelName = model.name || '';
            const id = modelName.replace(/^models\//, '');

            // Filter for gemini models suitable for content generation
            if (
                id.includes('gemini') &&
                !id.includes('embedding') &&
                !id.includes('aqa') &&
                !id.includes('imagen') &&
                !id.includes('veo')
            ) {
                models.push({
                    id,
                    name: model.displayName || id,
                    description: model.description || undefined
                });
            }
        }

        if (models.length === 0) {
            return FALLBACK_MODELS;
        }

        // Sort: 2.5 > 2.0 > 1.5, flash before pro
        return models.sort((a, b) => {
            const aVer = parseFloat(a.id.match(/(\d+\.?\d*)/)?.[1] || '0');
            const bVer = parseFloat(b.id.match(/(\d+\.?\d*)/)?.[1] || '0');
            if (bVer !== aVer) {
                return bVer - aVer;
            }
            return a.id.localeCompare(b.id);
        });
    } catch (error) {
        console.warn('[GeminiAdapter] Failed to list models, using fallback list:', error);
        return FALLBACK_MODELS;
    }
}

/**
 * Validates the provided API key by attempting a lightweight API call.
 */
export async function testApiKey(apiKey: string): Promise<{ success: boolean; message: string }> {
    if (!apiKey || apiKey.trim().length < 10) {
        return {success: false, message: 'Please provide a valid API key.'};
    }

    try {
        const ai = getAI(apiKey);
        // Request lightweight model list
        const pager = await ai.models.list({config: {pageSize: 1}});
        // Consume first element to confirm connection
        for await (const _ of pager) {
            break;
        }
        return {success: true, message: 'API key is valid and connected!'};
    } catch (error) {
        const errorMsg = await handleApiError(error);
        return {success: false, message: errorMsg};
    }
}

export interface GeminiContentPart {
    text?: string;
    inlineData?: {
        mimeType: string;
        data: string;
    };
}

export interface GeminiContent {
    role: 'user' | 'model';
    parts: GeminiContentPart[];
}

/**
 * Normalizes multi-turn conversation history to guarantee Gemini API compliance:
 * 1. Must start with 'user'
 * 2. Strictly alternate between 'user' and 'model'
 * 3. Ends with 'model' before the new turn's 'user' message
 */
export function normalizeHistoryForGemini(history: ConversationMessage[] = []): GeminiContent[] {
    if (!history || history.length === 0) return [];

    let recent = history.slice(-6);
    // Discard leading model/assistant messages
    while (recent.length > 0 && recent[0].role === 'assistant') {
        recent = recent.slice(1);
    }

    const contents: GeminiContent[] = [];

    for (const msg of recent) {
        if (!msg.content || !msg.content.trim()) continue;
        const role = msg.role === 'assistant' ? 'model' : 'user';

        if (contents.length > 0 && contents[contents.length - 1].role === role) {
            contents[contents.length - 1].parts.push({text: msg.content});
        } else {
            contents.push({
                role,
                parts: [{text: msg.content}]
            });
        }
    }

    // Since the next turn will be 'user', history must end with 'model'
    while (contents.length > 0 && contents[contents.length - 1].role === 'user') {
        contents.pop();
    }

    return contents;
}

/**
 * Queries Gemini with the captured screenshot, multi-turn context, and optional streaming.
 */
export async function queryGemini(
    dataUrl: string | null,
    settings: ExtensionSettings,
    history: ConversationMessage[] = [],
    customPrompt?: string,
    onChunk?: (chunk: string, fullText: string) => void
): Promise<string> {
    const {gemini_apiKey, gemini_model, gemini_prompt, gemini_timeout} = settings;

    try {
        if (!gemini_apiKey || !gemini_apiKey.trim()) {
            throw new Error('API key not configured. Please set it in Argus options.');
        }

        const ai = getAI(gemini_apiKey.trim());

        const contents: GeminiContent[] = normalizeHistoryForGemini(history);
        const currentTurnParts: GeminiContentPart[] = [];

        // Validate image format if provided
        if (dataUrl) {
            if (!dataUrl.includes(';base64,')) {
                throw new Error('Invalid image data URL');
            }
            const [mimeTypePart, base64Data] = dataUrl.split(';base64,');
            const mimeType = mimeTypePart.replace('data:', '');
            currentTurnParts.push({
                inlineData: {
                    mimeType,
                    data: base64Data
                }
            });
        }

        const effectivePrompt = customPrompt || gemini_prompt || 'Analyze the provided screen and answer concisely.';
        currentTurnParts.push({text: effectivePrompt});

        contents.push({
            role: 'user',
            parts: currentTurnParts
        });

        const timeoutMs = (gemini_timeout || 90) * 1000;
        let timeoutId: ReturnType<typeof setTimeout> | undefined;

        const timeoutPromise = new Promise<never>((_, reject) => {
            timeoutId = setTimeout(() => {
                reject(new Error(`Request timed out after ${gemini_timeout || 90}s`));
            }, timeoutMs);
        });

        try {
            if (onChunk) {
                const streamPromise = (async () => {
                    const responseStream = await ai.models.generateContentStream({
                        model: gemini_model || 'gemini-2.5-flash',
                        contents
                    });
                    let accumulated = '';
                    for await (const chunk of responseStream) {
                        const text = chunk.text || '';
                        if (text) {
                            accumulated += text;
                            onChunk(text, accumulated);
                        }
                    }
                    return accumulated || 'No textual response received from Gemini.';
                })();

                return await Promise.race([streamPromise, timeoutPromise]);
            } else {
                const generatePromise = ai.models.generateContent({
                    model: gemini_model || 'gemini-2.5-flash',
                    contents
                });

                const response = await Promise.race([generatePromise, timeoutPromise]);
                return response.text || 'No textual response received from Gemini.';
            }
        } finally {
            if (timeoutId) clearTimeout(timeoutId);
        }
    } catch (error) {
        return await handleApiError(error);
    }
}
