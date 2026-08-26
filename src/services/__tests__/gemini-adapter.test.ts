import {describe, expect, it} from 'vitest';
import {listModels, queryGemini, testApiKey} from '../gemini-adapter';
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
});
