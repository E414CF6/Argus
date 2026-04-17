/**
 * Session manager for conversation history
 */

import {type HistoryEntry, storageService} from './storage-service';
import {stateManager} from './state-manager';

export interface ConversationMessage {
    role: 'user' | 'assistant';
    content: string;
    imageDataUrl?: string;
    timestamp: number;
}

class SessionManager {
    private static instance: SessionManager;

    private constructor() {
    }

    static getInstance(): SessionManager {
        if (!SessionManager.instance) {
            SessionManager.instance = new SessionManager();
        }
        return SessionManager.instance;
    }

    async createSession(): Promise<string> {
        const sessionId = `s_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
        const now = Date.now();

        await storageService.saveSession(sessionId, {
            id: sessionId,
            title: `Session ${new Date(now).toLocaleString()}`,
            createdAt: now,
            updatedAt: now,
            messageCount: 0
        });
        await stateManager.setCurrentSessionId(sessionId);
        return sessionId;
    }

    async getCurrentSession(): Promise<string> {
        let sessionId = await stateManager.getCurrentSessionId();
        if (!sessionId || !(await storageService.getSession(sessionId))) {
            sessionId = await this.createSession();
        }
        return sessionId;
    }

    async addMessage(sessionId: string, role: 'user' | 'assistant', content: string, imageDataUrl?: string): Promise<void> {
        const entry: HistoryEntry = {
            id: `m_${Date.now()}`, sessionId, role, content, imageDataUrl, timestamp: Date.now()
        };
        await storageService.saveHistory(sessionId, entry);

        const session = await storageService.getSession(sessionId);
        if (session) {
            session.messageCount++;
            session.updatedAt = Date.now();
            await storageService.saveSession(sessionId, session);
        }
    }

    async getHistory(sessionId: string): Promise<ConversationMessage[]> {
        const entries = await storageService.getHistory(sessionId);
        return entries.map(e => ({
            role: e.role, content: e.content, imageDataUrl: e.imageDataUrl, timestamp: e.timestamp
        }));
    }
}

export const sessionManager = SessionManager.getInstance();
