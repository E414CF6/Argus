/**
 * State manager for Argus extension
 */

import {storageService} from './storage-service';
import {DEFAULT_SETTINGS, type ExtensionSettings} from '../types/settings';

class StateManager {
    private static instance: StateManager;
    private currentSessionId: string | null = null;
    private settingsCache: ExtensionSettings | null = null;
    private initPromise: Promise<void> | null = null;

    private constructor() {
        this.setupStorageListener();
    }

    static getInstance(): StateManager {
        if (!StateManager.instance) {
            StateManager.instance = new StateManager();
        }
        return StateManager.instance;
    }

    async initialize(): Promise<void> {
        if (!this.initPromise) {
            this.initPromise = (async () => {
                this.currentSessionId = await storageService.getSetting<string | null>('currentSessionId', null);
                this.settingsCache = await storageService.getSettings<ExtensionSettings>(DEFAULT_SETTINGS);
            })();
        }
        return this.initPromise;
    }

    async getCurrentSessionId(): Promise<string | null> {
        await this.initialize();
        return this.currentSessionId;
    }

    async setCurrentSessionId(sessionId: string | null): Promise<void> {
        await this.initialize();
        this.currentSessionId = sessionId;
        await storageService.setSetting('currentSessionId', sessionId);
    }

    async getSettings(): Promise<ExtensionSettings> {
        await this.initialize();
        if (!this.settingsCache) {
            this.settingsCache = await storageService.getSettings<ExtensionSettings>(DEFAULT_SETTINGS);
        }
        return {...this.settingsCache};
    }

    private setupStorageListener(): void {
        if (typeof chrome !== 'undefined' && chrome.storage?.onChanged) {
            chrome.storage.onChanged.addListener((changes, namespace) => {
                if (namespace === 'local') {
                    if (changes.currentSessionId) {
                        this.currentSessionId = (changes.currentSessionId.newValue as string | null) ?? null;
                    }

                    const hasSettingChange = Object.keys(changes).some(key => key !== 'currentSessionId');
                    if (hasSettingChange) {
                        this.settingsCache = null;
                    }
                }
            });
        }
    }
}

export const stateManager = StateManager.getInstance();
