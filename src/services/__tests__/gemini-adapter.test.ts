import {describe, expect, it} from 'vitest';
import {listModels, normalizeHistoryForGemini, queryGemini, testApiKey} from '../gemini-adapter';
import {DEFAULT_SETTINGS, FALLBACK_MODELS} from '../../types/settings';

describe('gemini-adapter', () => {
    it('returns fallback models when API key is empty or invalid', async () => {
        const models = await listModels('');
        expect(models).toEqual(FALLBACK_MODELS);
    });

    it('validates short API keys immediately in testApiKey', async () => {
        const result = await testApiKey('short');
        expect(result.success).toBe(false);
        expect(result.message).toContain('Please provide a valid API key');
    });

    it('rejects queryGemini when API key is missing', async () => {
        const settings = {...DEFAULT_SETTINGS, gemini_apiKey: ''};
        const result = await queryGemini('data:image/jpeg;base64,1234', settings);
        expect(result).toContain('Invalid or missing API key');
    });

    it('rejects queryGemini when dataUrl is invalid', async () => {
        const settings = {...DEFAULT_SETTINGS, gemini_apiKey: 'AIzaSyValidLengthKeyForTest12345678'};
        const result = await queryGemini('not-a-valid-data-url', settings);
        expect(result).toContain('Invalid image data URL');
    });

    it('normalizes history correctly: starts with user, strictly alternates, and ends before user turn', () => {
        // If history starts with assistant, leading assistant should be dropped
        const rawHistory = [
            {role: 'assistant' as const, content: 'Initial hello', timestamp: 1},
            {role: 'user' as const, content: 'Question 1', timestamp: 2},
            {role: 'assistant' as const, content: 'Answer 1', timestamp: 3},
            {role: 'user' as const, content: 'Question 2', timestamp: 4}
        ];
        const normalized = normalizeHistoryForGemini(rawHistory);
        // Leading assistant dropped, trailing user dropped so current user turn can be appended without duplicate
        expect(normalized.length).toBe(2);
        expect(normalized[0].role).toBe('user');
        expect(normalized[0].parts[0].text).toBe('Question 1');
        expect(normalized[1].role).toBe('model');
        expect(normalized[1].parts[0].text).toBe('Answer 1');
    });
});
