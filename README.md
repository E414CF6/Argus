# Argus

> **Quietly powerful AI visual assistant & exam helper Chrome extension with conversational sessions.**

Argus is a modern Chrome extension designed to help you analyze web pages and get answers instantly using **Google
Gemini AI**. By combining full-page visual context with a sophisticated session-based conversation system, Argus
provides more than just answers—it provides understanding.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Manifest V3](https://img.shields.io/badge/Manifest-V3-blue.svg)](https://developer.chrome.com/docs/extensions/mv3/intro/)
[![TypeScript](https://img.shields.io/badge/Language-TypeScript-blue.svg)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-7.x-purple.svg)](https://vitejs.dev/)
[![React](https://img.shields.io/badge/React-19.x-blue.svg)](https://react.dev/)

---

## ✨ Key Features

- **🤖 Google GenAI SDK v2 & Gemini 2.5/2.0**: Powered by the official `@google/genai` SDK and the latest Gemini
  multimodal models for fast, high-accuracy visual reasoning.
- **💬 Multi-Turn Session Conversations**: Preserves full multi-turn conversational context across queries for natural
  follow-up questions.
- **📸 Intelligent Page Capture**: Captures the active viewport and sends structured visual context to Gemini.
- **⌨️ Keyboard-First Workflow**: Designed for speed with global shortcuts for capture, overlay toggling, and new
  sessions.
- **🌓 Modern Floating Overlay**:
    - Header bar with drag handle and window management.
    - One-click copy, clear, and close buttons.
    - Markdown-style rich text formatting with inline code and bullet points.
    - Escape key dismiss and live style updates.
- **⚙️ Redesigned Options UI**:
    - API Key connection tester with live feedback.
    - Prompt template preset chips (Exam solver, step-by-step solutions, summarizer, code analyzer, translator).
    - Storage & local session data statistics and clearing tools.
- **🛡️ Privacy First**: All conversation histories and settings are stored locally on your device via Chrome Storage &
  IndexedDB.

---

## 🚀 Getting Started

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/E414CF6/Argus.git
   cd Argus
   ```
2. **Install dependencies:**
   ```bash
   npm install
   ```
3. **Build the extension:**
   ```bash
   npm run build
   ```
4. **Load into Chrome:**
    - Open Chrome and navigate to `chrome://extensions/`.
    - Enable **Developer mode** (top right toggle).
    - Click **Load unpacked** and select the `dist` folder in the project directory.

### Configuration

Before using Argus, configure your Gemini API key:

1. Obtain an API key from [Google AI Studio](https://aistudio.google.com/).
2. Open the Argus **Options** page (right-click the extension icon → Options, or open extension popup).
3. Paste your API key, click **Test** to verify connection, and click **Save Settings**.

---

## ⌨️ Keyboard Shortcuts

| Shortcut               | Action              | Description                                           |
|:-----------------------|:--------------------|:------------------------------------------------------|
| `Cmd/Ctrl + Shift + E` | **Capture & Query** | Capture visible page area and analyze with Gemini.    |
| `Cmd/Ctrl + Shift + D` | **Toggle Overlay**  | Show/hide the floating overlay without a new request. |
| `Cmd/Ctrl + Shift + N` | **New Session**     | Reset conversation history and start a fresh session. |
| `Esc`                  | **Dismiss Overlay** | Instantly close the active overlay.                   |

---

## 🏗️ Architecture & Tech Stack

Argus is built with a modular, service-oriented architecture:

- **Build Tool**: [Vite 7](https://vitejs.dev/) + [@crxjs/vite-plugin 2.7](https://crxjs.dev/) for lightning-fast HMR
  and bundle compilation.
- **Frontend**: [React 19](https://react.dev/) + TypeScript for a type-safe, reactive settings UI.
- **AI Core**: [@google/genai](https://www.npmjs.com/package/@google/genai) SDK v2 with native multi-turn conversation
  support.
- **State Management**: Centralized `StateManager` with real-time `chrome.storage.local` synchronization.
- **Storage Strategy**:
    - `Chrome Storage`: Extension configuration, styling preferences, and metadata.
    - `IndexedDB`: Local multi-turn session records and image history.
- **Test Suite**: [Vitest](https://vitest.dev/) with automated unit tests for adapters, managers, and message pipelines.

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).
