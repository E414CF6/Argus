import {describe, expect, it} from 'vitest';
import {DEFAULT_PROMPT, DEFAULT_SETTINGS, FALLBACK_MODELS, PROMPT_PRESETS, STEALTH_THEMES} from '../settings';

describe('settings defaults', () => {
    it('has expected default values prioritizing pure stealth and unblock drag', () => {
        expect(DEFAULT_SETTINGS.gemini_apiKey).toBe('');
        expect(DEFAULT_SETTINGS.gemini_model).toBe('gemini-2.5-flash');
        expect(DEFAULT_SETTINGS.gemini_prompt).toBe(DEFAULT_PROMPT);
        expect(DEFAULT_SETTINGS.gemini_timeout).toBe(90);
        expect(DEFAULT_SETTINGS.stealth_mode).toBe(true);
        expect(DEFAULT_SETTINGS.unblock_drag).toBe(true);
        expect(DEFAULT_SETTINGS.style_fontSize).toBe(11);
        expect(DEFAULT_SETTINGS.style_bgOpacity).toBe(0);
    });

    it('has non-empty default prompt, presets, and stealth themes with 0% opacity support', () => {
        expect(DEFAULT_PROMPT.length).toBeGreaterThan(0);
        expect(DEFAULT_PROMPT).toContain('Solve the problems');
        expect(PROMPT_PRESETS.length).toBeGreaterThanOrEqual(4);
        expect(FALLBACK_MODELS.length).toBeGreaterThanOrEqual(4);
        expect(STEALTH_THEMES.length).toBeGreaterThanOrEqual(4);
        expect(STEALTH_THEMES[0].settings.style_bgOpacity).toBe(0);
    });
});
