import {beforeEach, describe, expect, it, vi} from 'vitest';
import {sessionManager} from '../session-manager';
import {storageService} from '../storage-service';
import {stateManager} from '../state-manager';

describe('session-manager', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
    });

    it('creates a new session with unique ID and saves it', async () => {
        const saveSessionSpy = vi.spyOn(storageService, 'saveSession').mockResolvedValue();
        const setSessionSpy = vi.spyOn(stateManager, 'setCurrentSessionId').mockResolvedValue();

        const sessionId = await sessionManager.createSession('Custom Test Session');

        expect(sessionId).toMatch(/^s_\d+_/);
        expect(saveSessionSpy).toHaveBeenCalledWith(
            sessionId,
            expect.objectContaining({
                id: sessionId,
                title: 'Custom Test Session',
                messageCount: 0
            })
        );
        expect(setSessionSpy).toHaveBeenCalledWith(sessionId);
    });

    it('adds message and updates message count', async () => {
        const saveHistorySpy = vi.spyOn(storageService, 'saveHistory').mockResolvedValue();
        vi.spyOn(storageService, 'getSession').mockResolvedValue({
            id: 's_test_123',
            title: 'Session Title',
            createdAt: Date.now(),
            updatedAt: Date.now(),
            messageCount: 0
        });
        const saveSessionSpy = vi.spyOn(storageService, 'saveSession').mockResolvedValue();

        await sessionManager.addMessage('s_test_123', 'user', 'What is on the screen?', 'data:image/jpeg;base64,abc');

        expect(saveHistorySpy).toHaveBeenCalledWith(
            's_test_123',
            expect.objectContaining({
                sessionId: 's_test_123',
                role: 'user',
                content: 'What is on the screen?',
                imageDataUrl: 'data:image/jpeg;base64,abc'
            })
        );
        expect(saveSessionSpy).toHaveBeenCalledWith(
            's_test_123',
            expect.objectContaining({
                messageCount: 1
            })
        );
    });
});
