import {beforeEach, describe, expect, it, vi} from 'vitest';
import {queryWithContext} from '../session-context';
import {sessionManager} from '../session-manager';
import * as geminiAdapter from '../gemini-adapter';
import {DEFAULT_SETTINGS} from '../../types/settings';

describe('session-context', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
    });

    it('queries Gemini and saves successful conversation into history', async () => {
        vi.spyOn(sessionManager, 'getHistory').mockResolvedValue([
            {role: 'user', content: 'hello', timestamp: 100},
            {role: 'assistant', content: 'hi', timestamp: 101}
        ]);
        const querySpy = vi.spyOn(geminiAdapter, 'queryGemini').mockResolvedValue('Answer: 42');
        const addMessageSpy = vi.spyOn(sessionManager, 'addMessage').mockResolvedValue();

        const response = await queryWithContext('data:image/jpeg;base64,123', DEFAULT_SETTINGS, 's_test_1');

        expect(response).toBe('Answer: 42');
        expect(querySpy).toHaveBeenCalledWith('data:image/jpeg;base64,123', DEFAULT_SETTINGS, expect.any(Array), DEFAULT_SETTINGS.gemini_prompt, undefined);
        expect(addMessageSpy).toHaveBeenCalledTimes(2);
        expect(addMessageSpy).toHaveBeenNthCalledWith(1, 's_test_1', 'user', DEFAULT_SETTINGS.gemini_prompt);
        expect(addMessageSpy).toHaveBeenNthCalledWith(2, 's_test_1', 'assistant', 'Answer: 42');
    });

    it('does not save error responses into history', async () => {
        vi.spyOn(sessionManager, 'getHistory').mockResolvedValue([]);
        vi.spyOn(geminiAdapter, 'queryGemini').mockResolvedValue('Error: Rate limit exceeded');
        const addMessageSpy = vi.spyOn(sessionManager, 'addMessage').mockResolvedValue();

        const response = await queryWithContext('data:image/jpeg;base64,123', DEFAULT_SETTINGS, 's_test_1');

        expect(response).toContain('Error: Rate limit exceeded');
        expect(addMessageSpy).not.toHaveBeenCalled();
    });
});
