/**
 * Session manager for conversation history and sessions
 */

import {type HistoryEntry, type SessionData, storageService} from './storage-service';
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

    async createSession(title?: string): Promise<string> {
        const now = Date.now();
        const sessionId = `s_${now}_${Math.random().toString(36).substring(2, 8)}`;
        const sessionTitle = title || `Session ${new Date(now).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
        })}`;

        await storageService.saveSession(sessionId, {
            id: sessionId,
            title: sessionTitle,
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

    async addMessage(
        sessionId: string,
        role: 'user' | 'assistant',
        content: string,
        imageDataUrl?: string
    ): Promise<void> {
        const now = Date.now();
        const entry: HistoryEntry = {
            id: `m_${now}_${Math.random().toString(36).substring(2, 6)}`,
            sessionId,
            role,
            content,
            imageDataUrl,
            timestamp: now
        };
        await storageService.saveHistory(sessionId, entry);

        const session = await storageService.getSession(sessionId);
        if (session) {
            session.messageCount++;
            session.updatedAt = now;
            // If it's the first assistant response, use snippet for title if default
            if (session.messageCount <= 2 && role === 'assistant') {
                const snippet = content.trim().slice(0, 30).replace(/\n/g, ' ');
                if (snippet) {
                    session.title = `Q: ${snippet}...`;
                }
            }
            await storageService.saveSession(sessionId, session);
        }
    }

    async getHistory(sessionId: string): Promise<ConversationMessage[]> {
        const entries = await storageService.getHistory(sessionId);
        return entries.map(e => ({
            role: e.role,
            content: e.content,
            imageDataUrl: e.imageDataUrl,
            timestamp: e.timestamp
        }));
    }

    async getAllSessions(): Promise<SessionData[]> {
        return storageService.getAllSessions();
    }

    async deleteSession(sessionId: string): Promise<void> {
        await storageService.deleteSession(sessionId);
        const currentId = await stateManager.getCurrentSessionId();
        if (currentId === sessionId) {
            await stateManager.setCurrentSessionId(null);
        }
    }

    async clearAllSessions(): Promise<void> {
        await storageService.clearAllSessions();
        await stateManager.setCurrentSessionId(null);
    }
}

export const sessionManager = SessionManager.getInstance();
