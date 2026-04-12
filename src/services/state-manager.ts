/**
 * State manager for Argus extension
 */

import {storageService} from './storage-service';

export interface ExtensionSettings {
    gemini_apiKey: string;
    gemini_model: string;
    gemini_prompt: string;
    gemini_timeout: number;
    style_fontSize: number;
    style_textColor: string;
    style_bgColor: string;
    style_bgOpacity: number;
    style_maxWidth: number;
    style_maxHeight: number;

    [key: string]: unknown;
}

const DEFAULT_PROMPT = "Solve the problems on this page and only provide the answers. Skip the solution process and give concise answers. If the problems are cut off, ignore them and move on. If there are no problems, respond with 'No problems found.' Respond in the language used in the image. Answer in plain text without any additional formatting.";

export const DEFAULT_SETTINGS: ExtensionSettings = {
    gemini_apiKey: '',
    gemini_model: 'gemini-2.0-flash',
    gemini_prompt: DEFAULT_PROMPT,
    gemini_timeout: 90,
    style_fontSize: 13,
    style_textColor: '#e8e8e8',
    style_bgColor: '#1e1e1e',
    style_bgOpacity: 95,
    style_maxWidth: 400,
    style_maxHeight: 300,
};

class StateManager {
    private static instance: StateManager;
    private currentSessionId: string | null = null;
    private settingsCache: ExtensionSettings | null = null;

    private constructor() {
    }

    static getInstance(): StateManager {
        if (!StateManager.instance) {
            StateManager.instance = new StateManager();
        }
        return StateManager.instance;
    }

    async initialize(): Promise<void> {
        this.currentSessionId = await storageService.getSetting<string | null>('currentSessionId', null);
        this.settingsCache = await storageService.getSettings<ExtensionSettings>(DEFAULT_SETTINGS);
    }

    getCurrentSessionId(): string | null {
        return this.currentSessionId;
    }

    async setCurrentSessionId(sessionId: string | null): Promise<void> {
        this.currentSessionId = sessionId;
        await storageService.setSetting('currentSessionId', sessionId);
    }

    async getSettings(): Promise<ExtensionSettings> {
        if (!this.settingsCache) {
            this.settingsCache = await storageService.getSettings<ExtensionSettings>(DEFAULT_SETTINGS);
        }
        return {...this.settingsCache};
    }
}

export const stateManager = StateManager.getInstance();
