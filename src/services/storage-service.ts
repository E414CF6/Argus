/**
 * Storage service for managing Chrome Storage and IndexedDB
 * - Chrome Storage: Lightweight settings and metadata (5MB limit)
 * - IndexedDB: Large conversation history and session datasets
 */

const DB_NAME = 'ArgusDB';
const DB_VERSION = 1;
const STORE_SESSIONS = 'sessions';
const STORE_HISTORY = 'history';

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

export interface StorageService {
    // Chrome Storage
    getSetting<T>(key: string, defaultValue: T): Promise<T>;

    setSetting<T>(key: string, value: T): Promise<void>;

    getSettings<T extends object>(defaults: T): Promise<T>;

    setSettings<T extends object>(settings: Partial<T>): Promise<void>;

    // IndexedDB
    saveSession(sessionId: string, data: SessionData): Promise<void>;

    getSession(sessionId: string): Promise<SessionData | null>;

    getAllSessions(): Promise<SessionData[]>;

    deleteSession(sessionId: string): Promise<void>;

    clearAllSessions(): Promise<void>;

    saveHistory(sessionId: string, entry: HistoryEntry): Promise<void>;

    getHistory(sessionId: string): Promise<HistoryEntry[]>;

    clearHistory(sessionId: string): Promise<void>;
}

class StorageServiceImpl implements StorageService {
    private db: IDBDatabase | null = null;
    private dbInitPromise: Promise<IDBDatabase> | null = null;

    // Chrome Storage Methods
    async getSetting<T>(key: string, defaultValue: T): Promise<T> {
        try {
            const result = await chrome.storage.local.get({[key]: defaultValue});
            return result[key] as T;
        } catch (err) {
            console.error(`[StorageService] Failed to get setting '${key}':`, err);
            return defaultValue;
        }
    }

    async setSetting<T>(key: string, value: T): Promise<void> {
        try {
            await chrome.storage.local.set({[key]: value});
        } catch (err) {
            console.error(`[StorageService] Failed to set setting '${key}':`, err);
        }
    }

    async getSettings<T extends object>(defaults: T): Promise<T> {
        try {
            const result = await chrome.storage.local.get(defaults as unknown as { [key: string]: unknown });
            return result as T;
        } catch (err) {
            console.error('[StorageService] Failed to get settings:', err);
            return defaults;
        }
    }

    async setSettings<T extends object>(settings: Partial<T>): Promise<void> {
        try {
            await chrome.storage.local.set(settings as Record<string, unknown>);
        } catch (err) {
            console.error('[StorageService] Failed to set settings:', err);
        }
    }

    // IndexedDB Methods
    async saveSession(sessionId: string, data: SessionData): Promise<void> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_SESSIONS, 'readwrite');
            const store = tx.objectStore(STORE_SESSIONS);
            const request = store.put({...data, id: sessionId});

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

        await new Promise<void>((resolve, reject) => {
            const tx = db.transaction(STORE_SESSIONS, 'readwrite');
            const store = tx.objectStore(STORE_SESSIONS);
            const request = store.delete(sessionId);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });

        await this.clearHistory(sessionId);
    }

    async clearAllSessions(): Promise<void> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction([STORE_SESSIONS, STORE_HISTORY], 'readwrite');
            tx.objectStore(STORE_SESSIONS).clear();
            tx.objectStore(STORE_HISTORY).clear();

            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error);
        });
    }

    async saveHistory(sessionId: string, entry: HistoryEntry): Promise<void> {
        const db = await this.initDB();
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE_HISTORY, 'readwrite');
            const store = tx.objectStore(STORE_HISTORY);
            const request = store.put({...entry, sessionId});

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
                const entries: HistoryEntry[] = request.result || [];
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

    private async initDB(): Promise<IDBDatabase> {
        if (this.db) return this.db;

        if (this.dbInitPromise) return this.dbInitPromise;

        const idb = typeof indexedDB !== 'undefined' ? indexedDB : (typeof self !== 'undefined' && self.indexedDB) || null;
        if (!idb) {
            console.warn('[StorageService] IndexedDB not available in this environment');
            throw new Error('IndexedDB not available');
        }

        this.dbInitPromise = new Promise((resolve, reject) => {
            const request = idb.open(DB_NAME, DB_VERSION);

            request.onerror = () => {
                this.dbInitPromise = null;
                reject(request.error);
            };

            request.onsuccess = () => {
                this.db = request.result;
                this.db.onversionchange = () => {
                    this.db?.close();
                    this.db = null;
                    this.dbInitPromise = null;
                };
                resolve(this.db);
            };

            request.onupgradeneeded = (event) => {
                const db = (event.target as IDBOpenDBRequest).result;

                if (!db.objectStoreNames.contains(STORE_SESSIONS)) {
                    const sessionStore = db.createObjectStore(STORE_SESSIONS, {keyPath: 'id'});
                    sessionStore.createIndex('updatedAt', 'updatedAt', {unique: false});
                }

                if (!db.objectStoreNames.contains(STORE_HISTORY)) {
                    const historyStore = db.createObjectStore(STORE_HISTORY, {keyPath: 'id'});
                    historyStore.createIndex('sessionId', 'sessionId', {unique: false});
                    historyStore.createIndex('timestamp', 'timestamp', {unique: false});
                }
            };
        });

        return this.dbInitPromise;
    }
}

export const storageService = new StorageServiceImpl();
