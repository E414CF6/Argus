import './OptionsApp.css';

import React, {useCallback, useEffect, useState} from 'react';
import {
    DEFAULT_SETTINGS,
    type ExtensionSettings,
    FALLBACK_MODELS,
    PROMPT_PRESETS,
    type PromptPreset,
    STEALTH_THEMES,
    type StealthThemePreset
} from '../types/settings';
import {listModels, type ModelInfo, testApiKey} from '../services/gemini-adapter';
import {storageService} from '../services/storage-service';
import {getStorageValues, setStorageValues} from '../utils/chrome-helpers';

export default function OptionsApp() {
    const [settings, setSettings] = useState<ExtensionSettings>(DEFAULT_SETTINGS);
    const [status, setStatus] = useState<{ message: string; type: 'success' | 'error' | 'info' } | null>(null);
    const [models, setModels] = useState<ModelInfo[]>(FALLBACK_MODELS);
    const [loadingModels, setLoadingModels] = useState(false);
    const [testingKey, setTestingKey] = useState(false);
    const [sessionCount, setSessionCount] = useState<number>(0);
    const [activeTab, setActiveTab] = useState<'stealth' | 'api' | 'shortcuts' | 'data'>('stealth');

    const refreshSessionCount = useCallback(async () => {
        try {
            const sessions = await storageService.getAllSessions();
            setSessionCount(sessions.length);
        } catch {
            setSessionCount(0);
        }
    }, []);

    const fetchModels = useCallback(async (apiKey: string) => {
        if (!apiKey || apiKey.trim().length < 10) return;

        setLoadingModels(true);
        try {
            const fetched = await listModels(apiKey.trim());
            if (fetched && fetched.length > 0) {
                setModels(fetched);
            }
        } catch (error) {
            console.error('Failed to fetch models:', error);
        } finally {
            setLoadingModels(false);
        }
    }, []);

    useEffect(() => {
        getStorageValues<ExtensionSettings>(DEFAULT_SETTINGS)
            .then((items) => {
                setSettings(items);
                if (items.gemini_apiKey) {
                    void fetchModels(items.gemini_apiKey);
                }
            })
            .catch(console.error);

        void refreshSessionCount();
    }, [fetchModels, refreshSessionCount]);

    const handleChange = (
        e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
    ) => {
        const {name, value, type} = e.target;
        let parsedValue: string | number | boolean = value;

        if (type === 'checkbox') {
            parsedValue = (e.target as HTMLInputElement).checked;
        } else if (type === 'number') {
            const num = Number(value);
            if (!isNaN(num) && isFinite(num)) {
                parsedValue = num;
            }
        }

        setSettings((prev) => ({
            ...prev,
            [name]: parsedValue
        }));

        if (name === 'gemini_apiKey' && typeof value === 'string' && value.trim().length >= 30) {
            void fetchModels(value.trim());
        }
    };

    const handleApplyPromptPreset = (preset: PromptPreset) => {
        setSettings((prev) => ({
            ...prev,
            gemini_prompt: preset.prompt
        }));
        showStatusMessage(`Applied prompt: ${preset.name}`, 'info');
    };

    const handleApplyStealthTheme = (theme: StealthThemePreset) => {
        setSettings((prev) => ({
            ...prev,
            ...theme.settings
        }));
        showStatusMessage(`Applied stealth theme: ${theme.name}`, 'success');
    };

    const handleTestKey = async () => {
        if (!settings.gemini_apiKey.trim()) {
            showStatusMessage('Please enter an API key first.', 'error');
            return;
        }

        setTestingKey(true);
        try {
            const result = await testApiKey(settings.gemini_apiKey);
            if (result.success) {
                showStatusMessage('✅ Connection verified! Gemini API is ready.', 'success');
                void fetchModels(settings.gemini_apiKey);
            } else {
                showStatusMessage(`❌ ${result.message}`, 'error');
            }
        } catch {
            showStatusMessage('Connection test failed.', 'error');
        } finally {
            setTestingKey(false);
        }
    };

    const handleSave = async () => {
        try {
            await setStorageValues(settings);
            showStatusMessage('Settings saved successfully!', 'success');
        } catch (error) {
            console.error('Failed to save settings:', error);
            showStatusMessage('Failed to save settings!', 'error');
        }
    };

    const handleReset = () => {
        if (window.confirm('Reset all settings to stealth defaults? (API key will be cleared)')) {
            setSettings(DEFAULT_SETTINGS);
            setStorageValues(DEFAULT_SETTINGS)
                .then(() => showStatusMessage('Settings reset to stealth defaults.', 'info'))
                .catch(console.error);
        }
    };

    const handleClearSessions = async () => {
        if (window.confirm('Delete all saved conversation sessions and history?')) {
            try {
                await storageService.clearAllSessions();
                await refreshSessionCount();
                showStatusMessage('All conversation history cleared.', 'success');
            } catch (err) {
                console.error(err);
                showStatusMessage('Failed to clear history.', 'error');
            }
        }
    };

    const showStatusMessage = (message: string, type: 'success' | 'error' | 'info') => {
        setStatus({message, type});
        setTimeout(() => setStatus(null), 3000);
    };

    // Calculate hex to rgba for live preview
    const cleanHex = settings.style_bgColor.replace('#', '');
    const r = parseInt(cleanHex.slice(0, 2), 16) || 0;
    const g = parseInt(cleanHex.slice(2, 4), 16) || 0;
    const b = parseInt(cleanHex.slice(4, 6), 16) || 0;
    const previewBg = `rgba(${r}, ${g}, ${b}, ${settings.style_bgOpacity / 100})`;

    return (
        <div className="page">
            <header>
                <div className="logo-icon">👁️</div>
                <h1>Argus Settings</h1>
                <p>Quietly Powerful AI Visual Assistant</p>
                <div className="nav-tabs">
                    <button
                        className={`tab-btn ${activeTab === 'stealth' ? 'active' : ''}`}
                        onClick={() => setActiveTab('stealth')}
                    >
                        👻 Stealth & Appearance
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'api' ? 'active' : ''}`}
                        onClick={() => setActiveTab('api')}
                    >
                        🤖 AI & Prompts
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'shortcuts' ? 'active' : ''}`}
                        onClick={() => setActiveTab('shortcuts')}
                    >
                        ⌨️ Shortcuts
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'data' ? 'active' : ''}`}
                        onClick={() => setActiveTab('data')}
                    >
                        💾 Storage ({sessionCount})
                    </button>
                </div>
            </header>

            {/* TAB 1: STEALTH & CAMOUFLAGE (MAIN) */}
            {activeTab === 'stealth' && (
                <div className="panels-container">
                    <section className="panel">
                        <h2>Stealth & Camouflage Presets</h2>
                        <p className="section-desc">
                            Select an invisibility profile tailored to your display environment.
                        </p>

                        <div className="stealth-themes-grid">
                            {STEALTH_THEMES.map((theme) => (
                                <div
                                    key={theme.id}
                                    className="stealth-theme-card"
                                    onClick={() => handleApplyStealthTheme(theme)}
                                >
                                    <div className="theme-header">
                                        <strong>{theme.name}</strong>
                                    </div>
                                    <p>{theme.description}</p>
                                </div>
                            ))}
                        </div>

                        <h2 style={{marginTop: '24px'}}>Stealth Behaviors</h2>

                        <div className="form-group checkbox-group">
                            <label htmlFor="stealth_mode" className="checkbox-label">
                                <input
                                    type="checkbox"
                                    id="stealth_mode"
                                    name="stealth_mode"
                                    checked={settings.stealth_mode}
                                    onChange={handleChange}
                                />
                                <div>
                                    <strong>Stealth Mode (Invisibility)</strong>
                                    <small>Removes headers, borders, and heavy shadows. Shows clean, inconspicuous
                                        text.</small>
                                </div>
                            </label>
                        </div>

                        <div className="form-group checkbox-group">
                            <label htmlFor="stealth_hoverOnly" className="checkbox-label">
                                <input
                                    type="checkbox"
                                    id="stealth_hoverOnly"
                                    name="stealth_hoverOnly"
                                    checked={settings.stealth_hoverOnly}
                                    onChange={handleChange}
                                />
                                <div>
                                    <strong>Hover-Only Reveal</strong>
                                    <small>Nearly invisible (18% opacity) until you hover your mouse directly over
                                        it.</small>
                                </div>
                            </label>
                        </div>
                    </section>

                    <section className="panel">
                        <h2>Fine-Tune Overlay Appearance</h2>

                        <div className="preview-container">
                            <span className="preview-tag">Live Stealth Preview</span>
                            <div
                                className={`stealth-live-preview ${settings.stealth_hoverOnly ? 'preview-hover-stealth' : ''}`}
                                style={{
                                    background: previewBg,
                                    color: settings.style_textColor,
                                    fontSize: `${settings.style_fontSize}px`,
                                    border: settings.stealth_mode ? 'none' : '1px solid rgba(255,255,255,0.1)',
                                    borderRadius: settings.stealth_mode ? '4px' : '8px',
                                    boxShadow: settings.stealth_mode ? 'none' : '0 4px 16px rgba(0,0,0,0.3)',
                                }}
                            >
                                <div style={{opacity: 0.9}}>
                                    1. Q: (2x + 5 = 15) → <strong>x = 5</strong><br/>
                                    2. Answer: Option (C)<br/>
                                    3. Result verified.
                                </div>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="style_bgOpacity">Background Opacity</label>
                            <div className="form-control">
                                <div className="input-with-suffix">
                                    <input
                                        type="range"
                                        id="style_bgOpacity_slider"
                                        name="style_bgOpacity"
                                        value={settings.style_bgOpacity}
                                        onChange={handleChange}
                                        min="10"
                                        max="100"
                                        style={{flex: 1}}
                                    />
                                    <span className="suffix"
                                          style={{minWidth: '42px'}}>{settings.style_bgOpacity}%</span>
                                </div>
                                <small>Lower opacity blends the overlay naturally into the webpage</small>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="style_fontSize">Font Size</label>
                            <div className="form-control">
                                <div className="input-with-suffix">
                                    <input
                                        type="number"
                                        id="style_fontSize"
                                        name="style_fontSize"
                                        value={settings.style_fontSize}
                                        onChange={handleChange}
                                        min="9"
                                        max="20"
                                    />
                                    <span className="suffix">px</span>
                                </div>
                                <small>Smaller font sizes (10-12px) are less noticeable</small>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="style_textColor">Text Color</label>
                            <div className="form-control">
                                <div className="color-picker-wrapper">
                                    <input
                                        type="color"
                                        id="style_textColor"
                                        name="style_textColor"
                                        value={settings.style_textColor}
                                        onChange={handleChange}
                                    />
                                    <span className="color-value">{settings.style_textColor}</span>
                                </div>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="style_bgColor">Background Color</label>
                            <div className="form-control">
                                <div className="color-picker-wrapper">
                                    <input
                                        type="color"
                                        id="style_bgColor"
                                        name="style_bgColor"
                                        value={settings.style_bgColor}
                                        onChange={handleChange}
                                    />
                                    <span className="color-value">{settings.style_bgColor}</span>
                                </div>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="style_maxWidth">Max Size</label>
                            <div className="form-control">
                                <div className="input-with-suffix">
                                    <input
                                        type="number"
                                        id="style_maxWidth"
                                        name="style_maxWidth"
                                        value={settings.style_maxWidth}
                                        onChange={handleChange}
                                        min="180"
                                        max="1000"
                                        placeholder="W"
                                    />
                                    <span className="suffix">×</span>
                                    <input
                                        type="number"
                                        id="style_maxHeight"
                                        name="style_maxHeight"
                                        value={settings.style_maxHeight}
                                        onChange={handleChange}
                                        min="120"
                                        max="1000"
                                        placeholder="H"
                                    />
                                    <span className="suffix">px</span>
                                </div>
                            </div>
                        </div>
                    </section>
                </div>
            )}

            {/* TAB 2: AI & PROMPTS */}
            {activeTab === 'api' && (
                <div className="panels-container">
                    <section className="panel">
                        <h2>API Configuration</h2>

                        <div className="form-group">
                            <label htmlFor="gemini_apiKey">API Key *</label>
                            <div className="form-control">
                                <div className="input-with-button">
                                    <input
                                        type="password"
                                        id="gemini_apiKey"
                                        name="gemini_apiKey"
                                        value={settings.gemini_apiKey}
                                        onChange={handleChange}
                                        placeholder="Enter your Gemini API key"
                                    />
                                    <button
                                        type="button"
                                        className="secondary-btn"
                                        onClick={handleTestKey}
                                        disabled={testingKey || !settings.gemini_apiKey}
                                    >
                                        {testingKey ? 'Testing...' : 'Test'}
                                    </button>
                                </div>
                                <small>
                                    <a
                                        href="https://aistudio.google.com/apikey"
                                        target="_blank"
                                        rel="noopener noreferrer"
                                    >
                                        Get free API key from Google AI Studio →
                                    </a>
                                </small>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="gemini_model">
                                Model {loadingModels && <span className="loading">Loading...</span>}
                            </label>
                            <div className="form-control">
                                <select
                                    id="gemini_model"
                                    name="gemini_model"
                                    value={settings.gemini_model}
                                    onChange={handleChange}
                                    disabled={loadingModels}
                                >
                                    {models.map((m) => (
                                        <option key={m.id} value={m.id} title={m.description}>
                                            {m.name || m.id}
                                        </option>
                                    ))}
                                </select>
                                <small>{models.length} model options available</small>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="gemini_timeout">Timeout (s)</label>
                            <div className="form-control">
                                <input
                                    type="number"
                                    id="gemini_timeout"
                                    name="gemini_timeout"
                                    value={settings.gemini_timeout}
                                    onChange={handleChange}
                                    min="30"
                                    max="300"
                                />
                                <small>Max response wait duration (30-300 seconds)</small>
                            </div>
                        </div>
                    </section>

                    <section className="panel">
                        <h2>Prompt Templates</h2>

                        <div className="form-group vertical">
                            <div className="label-with-presets">
                                <label htmlFor="gemini_prompt">Active Prompt</label>
                                <div className="preset-chips">
                                    {PROMPT_PRESETS.map((preset) => (
                                        <button
                                            key={preset.id}
                                            type="button"
                                            className="preset-chip"
                                            onClick={() => handleApplyPromptPreset(preset)}
                                            title={preset.description}
                                        >
                                            {preset.name.split(' ')[0]} {preset.name.split(' ')[1]}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <div className="form-control full-width">
                                <textarea
                                    id="gemini_prompt"
                                    name="gemini_prompt"
                                    value={settings.gemini_prompt}
                                    onChange={handleChange}
                                    placeholder="Instructions for analyzing captured screens"
                                    rows={10}
                                />
                            </div>
                        </div>
                    </section>
                </div>
            )}

            {/* TAB 3: KEYBOARD SHORTCUTS */}
            {activeTab === 'shortcuts' && (
                <div className="panel full-width-panel">
                    <h2>Keyboard Shortcuts Guide</h2>
                    <p className="section-desc">
                        Argus is built to be fast, quiet, and keyboard-first. Use these key combinations anywhere on web
                        pages.
                    </p>

                    <div className="shortcuts-table">
                        <div className="shortcut-row">
                            <div className="shortcut-keys">
                                <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>E</kbd>
                                <span className="or-badge">or</span>
                                <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>E</kbd>
                            </div>
                            <div className="shortcut-info">
                                <strong>Capture & Query</strong>
                                <span>Captures the visible screen area and sends it with your prompt to Gemini.</span>
                            </div>
                        </div>

                        <div className="shortcut-row">
                            <div className="shortcut-keys">
                                <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>D</kbd>
                                <span className="or-badge">or</span>
                                <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>D</kbd>
                            </div>
                            <div className="shortcut-info">
                                <strong>Toggle Overlay</strong>
                                <span>Shows or hides the response overlay quietly without making a request.</span>
                            </div>
                        </div>

                        <div className="shortcut-row">
                            <div className="shortcut-keys">
                                <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>N</kbd>
                                <span className="or-badge">or</span>
                                <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>N</kbd>
                            </div>
                            <div className="shortcut-info">
                                <strong>New Conversation Session</strong>
                                <span>Resets multi-turn context and starts a fresh conversation session.</span>
                            </div>
                        </div>

                        <div className="shortcut-row">
                            <div className="shortcut-keys">
                                <kbd>Esc</kbd>
                            </div>
                            <div className="shortcut-info">
                                <strong>Dismiss Overlay</strong>
                                <span>Instantly dismisses the overlay window.</span>
                            </div>
                        </div>
                    </div>

                    <div className="shortcut-footer">
                        <span>Want to customize shortcuts?</span>
                        <a
                            href="chrome://extensions/shortcuts"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="secondary-link"
                            onClick={(e) => {
                                e.preventDefault();
                                chrome.tabs.create({url: 'chrome://extensions/shortcuts'});
                            }}
                        >
                            Open Chrome Shortcuts Manager →
                        </a>
                    </div>
                </div>
            )}

            {/* TAB 4: STORAGE */}
            {activeTab === 'data' && (
                <div className="panel full-width-panel">
                    <h2>Storage & History</h2>
                    <p className="section-desc">
                        Argus saves conversation sessions and responses locally in IndexedDB on your browser for
                        privacy.
                    </p>

                    <div className="storage-stats">
                        <div className="stat-card">
                            <span className="stat-value">{sessionCount}</span>
                            <span className="stat-label">Stored Sessions</span>
                        </div>
                        <div className="stat-card">
                            <span className="stat-value">Local IndexedDB</span>
                            <span className="stat-label">Storage Engine</span>
                        </div>
                    </div>

                    <div className="danger-zone">
                        <h3>Session Data Management</h3>
                        <p>Clear all local conversation history and cached session records.</p>
                        <button type="button" className="danger-button" onClick={handleClearSessions}>
                            🗑️ Clear All Sessions
                        </button>
                    </div>
                </div>
            )}

            <div className="footer">
                <button className="reset-button" onClick={handleReset}>
                    Reset to Stealth Defaults
                </button>
                <button className="save-button" onClick={handleSave}>
                    Save Settings
                </button>
            </div>

            {status && (
                <div className={`status show ${status.type}`}>
                    {status.type === 'success' ? '✅' : status.type === 'error' ? '❌' : 'ℹ️'} {status.message}
                </div>
            )}
        </div>
    );
}
