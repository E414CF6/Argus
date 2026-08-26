export interface ExtensionSettings {
    gemini_apiKey: string;
    gemini_model: string;
    gemini_prompt: string;
    gemini_timeout: number;
    // Stealth & Appearance
    stealth_mode: boolean;
    style_fontSize: number;
    style_textColor: string;
    style_bgColor: string;
    style_bgOpacity: number;
    style_maxWidth: number;
    style_maxHeight: number;
    overlay_x: number;
    overlay_y: number;
    [key: string]: string | number | boolean | unknown;
}

export interface PromptPreset {
    id: string;
    name: string;
    description: string;
    prompt: string;
}

export interface StealthThemePreset {
    id: string;
    name: string;
    description: string;
    settings: {
        stealth_mode: boolean;
        style_fontSize: number;
        style_textColor: string;
        style_bgColor: string;
        style_bgOpacity: number;
        style_maxWidth: number;
        style_maxHeight: number;
    };
}

export const DEFAULT_PROMPT = "Solve the problems on this page and only provide the answers. Skip the solution process and give concise answers. If the problems are cut off, ignore them and move on. If there are no problems, respond with 'No problems found.' Respond in the language used in the image. Answer in plain text without any additional formatting.";

export const PROMPT_PRESETS: PromptPreset[] = [
    {
        id: 'exam_solver',
        name: '🎯 Exam / Quick Answers (Default)',
        description: 'Directly solves questions with concise answers without unnecessary fluff.',
        prompt: DEFAULT_PROMPT,
    },
    {
        id: 'detailed_solution',
        name: '📚 Step-by-Step Solutions',
        description: 'Provides thorough explanations, formulas, and reasoning for all problems.',
        prompt: "Analyze the questions and problems on this screen. Provide clear, step-by-step solutions with detailed reasoning and final answers. Respond in the primary language used on the page."
    },
    {
        id: 'page_summary',
        name: '📝 Page Summarizer',
        description: 'Summarizes key points, main ideas, and critical takeaways from the screen.',
        prompt: "Provide a concise, bulleted executive summary of the content visible in this screenshot. Highlight key arguments, data, and conclusions in the language of the document."
    },
    {
        id: 'code_analyzer',
        name: '💻 Code Analyzer & Debugger',
        description: 'Identifies bugs, explains algorithms, and suggests code improvements.',
        prompt: "Analyze the code, error messages, or technical diagrams in this screenshot. Explain what the code does, pinpoint any bugs or performance issues, and provide corrected code snippets if needed."
    },
    {
        id: 'translator',
        name: '🌐 Translation & Explanation',
        description: 'Translates visible text to English/Korean and clarifies difficult terms.',
        prompt: "Translate all foreign text on this page into Korean (or English if original is Korean). Clarify any specialized terms, idioms, or cultural nuances."
    }
];

export const STEALTH_THEMES: StealthThemePreset[] = [
    {
        id: 'invisible_bg',
        name: '👻 100% Transparent (No Background)',
        description: '0% background opacity. Only floating text is visible, completely blending in.',
        settings: {
            stealth_mode: true,
            style_fontSize: 11,
            style_textColor: '#a3a3a3',
            style_bgColor: '#000000',
            style_bgOpacity: 0,
            style_maxWidth: 380,
            style_maxHeight: 280,
        }
    },
    {
        id: 'ghost_stealth',
        name: '🌫️ Ultra-Low Ghost (15% Opacity)',
        description: 'Almost fully transparent dark tint for slightly improved contrast.',
        settings: {
            stealth_mode: true,
            style_fontSize: 11,
            style_textColor: '#999999',
            style_bgColor: '#121214',
            style_bgOpacity: 15,
            style_maxWidth: 380,
            style_maxHeight: 280,
        }
    },
    {
        id: 'low_contrast_dark',
        name: '🕶️ Low-Contrast Dark (35% Opacity)',
        description: 'Subtle dark mode with muted gray text. Barely visible at a glance.',
        settings: {
            stealth_mode: true,
            style_fontSize: 11,
            style_textColor: '#888888',
            style_bgColor: '#09090b',
            style_bgOpacity: 35,
            style_maxWidth: 380,
            style_maxHeight: 280,
        }
    },
    {
        id: 'document_camouflage',
        name: '📄 Document Camouflage (Light / 0% BG)',
        description: 'Dark ink text with 0% transparent background for white exam papers & PDFs.',
        settings: {
            stealth_mode: true,
            style_fontSize: 11,
            style_textColor: '#52525b',
            style_bgColor: '#ffffff',
            style_bgOpacity: 0,
            style_maxWidth: 400,
            style_maxHeight: 300,
        }
    },
    {
        id: 'code_comment',
        name: '💻 Code Comment Style (0% BG)',
        description: 'Muted green code font with 0% background, disguised as IDE comments.',
        settings: {
            stealth_mode: true,
            style_fontSize: 11,
            style_textColor: '#6a9955',
            style_bgColor: '#1e1e1e',
            style_bgOpacity: 0,
            style_maxWidth: 420,
            style_maxHeight: 300,
        }
    }
];

export const FALLBACK_MODELS = [
    { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash', description: 'Next-gen fast multimodal model' },
    { id: 'gemini-2.5-pro', name: 'Gemini 2.5 Pro', description: 'Next-gen advanced multimodal reasoning model' },
    { id: 'gemini-2.0-flash', name: 'Gemini 2.0 Flash', description: 'Fast, versatile model for general visual queries' },
    { id: 'gemini-2.0-flash-lite', name: 'Gemini 2.0 Flash Lite', description: 'Lightweight high-efficiency model' },
    { id: 'gemini-1.5-flash', name: 'Gemini 1.5 Flash', description: 'Fast and lightweight model' },
    { id: 'gemini-1.5-pro', name: 'Gemini 1.5 Pro', description: 'High reasoning capabilities for complex visual tasks' }
];

export const DEFAULT_SETTINGS: ExtensionSettings = {
    gemini_apiKey: '',
    gemini_model: 'gemini-2.5-flash',
    gemini_prompt: DEFAULT_PROMPT,
    gemini_timeout: 90,
    // Stealth & Camouflage defaults: 0% transparent background, minimal font
    stealth_mode: true,
    style_fontSize: 11,
    style_textColor: '#a3a3a3',
    style_bgColor: '#000000',
    style_bgOpacity: 0,
    style_maxWidth: 380,
    style_maxHeight: 280,
    overlay_x: -1,
    overlay_y: -1,
};
