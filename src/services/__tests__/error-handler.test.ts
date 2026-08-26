import {describe, expect, it} from 'vitest';
import {handleApiError} from '../error-handler';

describe('error-handler', () => {
    it('handles API key errors correctly', async () => {
        const result = await handleApiError(new Error('Invalid API key provided'));
        expect(result).toContain('Invalid or missing API key');
    });

    it('handles timeout errors correctly', async () => {
        const result = await handleApiError(new Error('Request timeout after 90s'));
        expect(result).toContain('Request timed out');
    });

    it('handles rate limit errors (429)', async () => {
        const result = await handleApiError(new Error('HTTP 429 Too Many Requests'));
        expect(result).toContain('Rate limit or quota exceeded');
    });

    it('handles server errors (500)', async () => {
        const result = await handleApiError(new Error('HTTP 500 Internal Server Error'));
        expect(result).toContain('Gemini API server is temporarily unavailable');
    });

    it('handles generic unknown errors', async () => {
        const result = await handleApiError(new Error('Custom unexpected error'));
        expect(result).toBe('Error: Custom unexpected error');
    });
});
