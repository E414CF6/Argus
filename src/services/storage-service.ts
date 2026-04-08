/**
 * Storage service for managing Chrome Storage and IndexedDB
 * Chrome Storage: Small data like settings (5MB limit)
 * IndexedDB: Large data like session history (unlimited)
 */

const DB_NAME = 'ArgusDB';
const DB_VERSION = 1;
const STORE_SESSIONS = 'sessions';
const STORE_HISTORY = 'history';

interface StorageService {
    // Chrome Storage methods
    getSetting<T>(key: string, defaultValue: T): Promise<T>;

    setSetting<T>(key: string, value: T): Promise<void>;

    getSettings<T extends Record<string, any>>(defaults: T): Promise<T>;

    setSettings<T extends Record<string, any>>(settings: Partial<T>): Promise<void>;

    // IndexedDB methods
    saveSession(sessionId: string, data: SessionData): Promise<void>;

    getSession(sessionId: string): Promise<SessionData | null>;

    getAllSessions(): Promise<SessionData[]>;

    deleteSession(sessionId: string): Promise<void>;

    saveHistory(sessionId: string, entry: HistoryEntry): Promise<void>;

    getHistory(sessionId: string): Promise<HistoryEntry[]>;

    clearHistory(sessionId: string): Promise<void>;
}

export interface SessionData {
    id: string;
    title: string;
    createdAt: number;
    updatedAt: number;
    messageCount: number;
}

export interface HistoryEntry {
    id: string;
    sessionId: string;
    role: 'user' | 'assistant';
    content: string;
    imageDataUrl?: string;
    timestamp: number;
}

class StorageServiceImpl implements StorageService {
    private db: IDBDatabase | null = null;

    // Chrome Storage methods
    async getSetting<T>(key: string, defaultValue: T): Promise<T> {
        const result = await chrome.storage.local.get({[key]: defaultValue});
        return result[key] as T;
    }

    async setSetting<T>(key: string, value: T): Promise<void> {
        await chrome.storage.local.set({[key]: value});
    }

    async getSettings<T extends Record<string, any>>(defaults: T): Promise<T> {
        const result = await chrome.storage.local.get(defaults);
        return result as T;
    }

    async setSettings<T extends Record<string, any>>(settings: Partial<T>): Promise<void> {
        await chrome.storage.local.set(settings);
    }

    // IndexedDB methods
    async saveSession(sessionId: string, data: SessionData): Promise<void> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_SESSIONS, 'readwrite');
            const store = tx.objectStore(STORE_SESSIONS);
            const request = store.put(data);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }

    async getSession(sessionId: string): Promise<SessionData | null> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_SESSIONS, 'readonly');
            const store = tx.objectStore(STORE_SESSIONS);
            const request = store.get(sessionId);

            request.onsuccess = () => resolve(request.result || null);
            request.onerror = () => reject(request.error);
        });
    }

    async getAllSessions(): Promise<SessionData[]> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_SESSIONS, 'readonly');
            const store = tx.objectStore(STORE_SESSIONS);
            const index = store.index('updatedAt');
            const request = index.openCursor(null, 'prev'); // Most recent first

            const sessions: SessionData[] = [];
            request.onsuccess = () => {
                const cursor = request.result;
                if (cursor) {
                    sessions.push(cursor.value);
                    cursor.continue();
                } else {
                    resolve(sessions);
                }
            };
            request.onerror = () => reject(request.error);
        });
    }

    async deleteSession(sessionId: string): Promise<void> {
        const db = await this.initDB();

        // Delete session
        await new Promise<void>((resolve, reject) => {
            const tx = db.transaction(STORE_SESSIONS, 'readwrite');
            const store = tx.objectStore(STORE_SESSIONS);
            const request = store.delete(sessionId);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });

        // Delete associated history
        await this.clearHistory(sessionId);
    }

    async saveHistory(sessionId: string, entry: HistoryEntry): Promise<void> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_HISTORY, 'readwrite');
            const store = tx.objectStore(STORE_HISTORY);
            const request = store.put(entry);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }

    async getHistory(sessionId: string): Promise<HistoryEntry[]> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_HISTORY, 'readonly');
            const store = tx.objectStore(STORE_HISTORY);
            const index = store.index('sessionId');
            const request = index.getAll(sessionId);

            request.onsuccess = () => {
                const entries = request.result || [];
                // Sort by timestamp
                entries.sort((a, b) => a.timestamp - b.timestamp);
                resolve(entries);
            };
            request.onerror = () => reject(request.error);
        });
    }

    async clearHistory(sessionId: string): Promise<void> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_HISTORY, 'readwrite');
            const store = tx.objectStore(STORE_HISTORY);
            const index = store.index('sessionId');
            const request = index.openCursor(IDBKeyRange.only(sessionId));

            request.onsuccess = () => {
                const cursor = request.result;
                if (cursor) {
                    cursor.delete();
                    cursor.continue();
                } else {
                    resolve();
                }
            };
            request.onerror = () => reject(request.error);
        });
    }

    // Initialize IndexedDB
    private async initDB(): Promise<IDBDatabase> {
        if (this.db) return this.db;

        // Check if we're in a compatible environment
        const idb = typeof indexedDB !== 'undefined' ? indexedDB : (typeof self !== 'undefined' && self.indexedDB) || null;

        if (!idb) {
            console.warn('[WARN] IndexedDB not available in this environment');
            throw new Error('IndexedDB not available');
        }

        return new Promise((resolve, reject) => {
            const request = idb.open(DB_NAME, DB_VERSION);

            request.onerror = () => reject(request.error);
            request.onsuccess = () => {
                this.db = request.result;
                resolve(this.db);
            };

            request.onupgradeneeded = (event) => {
                const db = (event.target as IDBOpenDBRequest).result;

                // Sessions store
                if (!db.objectStoreNames.contains(STORE_SESSIONS)) {
                    const sessionStore = db.createObjectStore(STORE_SESSIONS, {keyPath: 'id'});
                    sessionStore.createIndex('updatedAt', 'updatedAt', {unique: false});
                }

                // History store
                if (!db.objectStoreNames.contains(STORE_HISTORY)) {
                    const historyStore = db.createObjectStore(STORE_HISTORY, {keyPath: 'id'});
                    historyStore.createIndex('sessionId', 'sessionId', {unique: false});
                    historyStore.createIndex('timestamp', 'timestamp', {unique: false});
                }
            };
        });
    }
}

// Singleton instance
export const storageService = new StorageServiceImpl();
