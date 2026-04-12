import './OptionsApp.css';

import React, {useCallback, useEffect, useState} from 'react';

import {DEFAULT_SETTINGS} from '../services/state-manager';
import {listModels, type ModelInfo} from '../services/gemini-adapter';
import {getStorageValues, setStorageValues} from '../utils/chrome-helpers';

const DEFAULT_MODELS: ModelInfo[] = [{id: 'gemini-2.0-flash', name: 'Gemini 2.0 Flash'}, {
    id: 'gemini-2.0-flash-lite', name: 'Gemini 2.0 Flash Lite'
},];

export default function OptionsApp() {
    const [settings, setSettings] = useState<any>(DEFAULT_SETTINGS);
    const [status, setStatus] = useState<{ message: string; type: string } | null>(null);
    const [models, setModels] = useState<ModelInfo[]>(DEFAULT_MODELS);
    const [loadingModels, setLoadingModels] = useState(false);

    const fetchModels = useCallback(async (apiKey: string) => {
        if (!apiKey || apiKey.length < 10) return;

        setLoadingModels(true);
        try {
            const fetchedModels = await listModels(apiKey);
            if (fetchedModels.length > 0) {
                setModels(fetchedModels);
            }
        } catch (error) {
            console.error('Failed to fetch models:', error);
        } finally {
            setLoadingModels(false);
        }
    }, []);

    useEffect(() => {
        getStorageValues(DEFAULT_SETTINGS).then((items) => {
            setSettings(items);
            if (items.gemini_apiKey) {
                fetchModels(items.gemini_apiKey as string);
            }
        }).catch(err => {
            console.error(err);
        });
    }, [fetchModels]);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const {name, value, type} = e.target;
        let parsedValue: string | number = value;

        if (type === 'number') {
            const num = Number(value);
            if (!isNaN(num) && isFinite(num)) {
                parsedValue = num;
            }
        }

        setSettings((prev: any) => ({
            ...prev, [name]: parsedValue
        }));

        // Fetch models when API key changes
        if (name === 'gemini_apiKey' && value.length >= 30) {
            fetchModels(value);
        }
    };

    const handleSave = async () => {
        if (!settings.gemini_apiKey.trim()) {
            showStatusMessage('API Key is required.', 'error');
            return;
        }

        try {
            await setStorageValues(settings);
            showStatusMessage('Settings saved!', 'success');
        } catch (error) {
            console.error('Failed to save settings:', error);
            showStatusMessage('Failed to save settings!', 'error');
        }
    };

    const handleReset = () => {
        if (window.confirm('Reset to default settings? API key will be cleared.')) {
            setSettings(DEFAULT_SETTINGS);
            setStorageValues(DEFAULT_SETTINGS);
        }
    };

    const showStatusMessage = (message: string, type: string) => {
        setStatus({message, type});
        setTimeout(() => setStatus(null), 2500);
    };

    return (<div className="page">
        <header>
            <div className="logo-icon">👁️</div>
            <h1>Argus Settings</h1>
            <p>Quietly Powerful</p>
        </header>

        <div className="panels-container">
            <section className="panel">
                <h2>API Configuration</h2>

                <div className="form-group">
                    <label htmlFor="gemini_apiKey">API Key *</label>
                    <div className="form-control">
                        <input
                            type="password"
                            id="gemini_apiKey"
                            name="gemini_apiKey"
                            value={settings.gemini_apiKey}
                            onChange={handleChange}
                            placeholder="Enter your Gemini API key"
                        />
                        <small>
                            <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer">
                                Get API key from Google AI Studio →
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
                            {models.map(m => (<option key={m.id} value={m.id} title={m.description}>
                                {m.name || m.id}
                            </option>))}
                        </select>
                        {models.length > DEFAULT_MODELS.length && (<small>{models.length} models available</small>)}
                    </div>
                </div>

                <div className="form-group">
                    <label htmlFor="gemini_prompt">Default Prompt</label>
                    <div className="form-control">
                        <textarea
                            id="gemini_prompt"
                            name="gemini_prompt"
                            value={settings.gemini_prompt}
                            onChange={handleChange}
                            placeholder="Instructions for analyzing captured pages"
                            rows={14}
                        />
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
                        <small>Wait time for API response (30-300)</small>
                    </div>
                </div>
            </section>

            <section className="panel">
                <h2>Overlay Style</h2>

                <div className="hint">
                    <span>💡</span>
                    <span>Drag the overlay handle to reposition it anywhere on the screen.</span>
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
                                min="10"
                                max="24"
                            />
                            <span className="suffix">px</span>
                        </div>
                    </div>
                </div>

                <div className="form-group">
                    <label htmlFor="style_maxWidth">Max Width</label>
                    <div className="form-control">
                        <div className="input-with-suffix">
                            <input
                                type="number"
                                id="style_maxWidth"
                                name="style_maxWidth"
                                value={settings.style_maxWidth || 400}
                                onChange={handleChange}
                                min="100"
                                max="1000"
                            />
                            <span className="suffix">px</span>
                        </div>
                        <small>Max horizontal size of the overlay</small>
                    </div>
                </div>

                <div className="form-group">
                    <label htmlFor="style_maxHeight">Max Height</label>
                    <div className="form-control">
                        <div className="input-with-suffix">
                            <input
                                type="number"
                                id="style_maxHeight"
                                name="style_maxHeight"
                                value={settings.style_maxHeight || 300}
                                onChange={handleChange}
                                min="100"
                                max="1000"
                            />
                            <span className="suffix">px</span>
                        </div>
                        <small>Max vertical size of the overlay before scrolling</small>
                    </div>
                </div>

                <div className="form-group">
                    <label htmlFor="style_bgOpacity">Opacity</label>
                    <div className="form-control">
                        <div className="input-with-suffix">
                            <input
                                type="number"
                                id="style_bgOpacity"
                                name="style_bgOpacity"
                                value={settings.style_bgOpacity}
                                onChange={handleChange}
                                min="50"
                                max="100"
                            />
                            <span className="suffix">%</span>
                        </div>
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
                    <label htmlFor="style_bgColor">Background</label>
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
            </section>
        </div>

        <div className="footer">
            <button className="reset-button" onClick={handleReset}>Reset</button>
            <button className="save-button" onClick={handleSave}>Save Settings</button>
        </div>

        {status && (<div className={`status show ${status.type}`}>
            {status.type === 'success' ? '✅' : '❌'} {status.message}
        </div>)}
    </div>);
}
