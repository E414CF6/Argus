# Argus

> **Quietly powerful exam helper chrome extension with conversational sessions.**

Argus is a modern Chrome extension designed to help you analyze web pages and get answers instantly using **Gemini AI**.
By combining full-page visual context with a sophisticated session-based conversation system, Argus provides more than
just answers—it provides understanding.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Manifest V3](https://img.shields.io/badge/Manifest-V3-blue.svg)](https://developer.chrome.com/docs/extensions/mv3/intro/)
[![TypeScript](https://img.shields.io/badge/Language-TypeScript-blue.svg)](https://www.typescriptlang.org/)

---

## ✨ Key Features

- **🤖 Gemini AI Integration**: Powered by the latest Gemini vision models for deep analysis of both text and visual
  elements.
- **💬 Session-Based Conversations**: Maintains context across multiple queries, allowing for a natural back-and-forth
  dialogue.
- **📸 Intelligent Page Capture**: Captures the visible area of the active tab to provide the AI with full visual
  context.
- **⌨️ Pro Keyboard Shortcuts**: Designed for speed, letting you query and navigate without touching your mouse.
- **🌓 Modern UI**: Sleek, non-intrusive React-based overlay that integrates seamlessly into any webpage.
- **🛡️ Privacy First**: Your data stays local. We use Chrome Storage and IndexedDB for persistent history.

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
    - Enable **Developer mode** (top right).
    - Click **Load unpacked** and select the `dist` folder in the project directory.

### Configuration

Before using Argus, you need a Gemini API key:

1. Obtain an API key from the [Google AI Studio](https://aistudio.google.com/).
2. Open the Argus **Options** page (right-click the extension icon -> Options).
3. Paste your API key and save.

---

## ⌨️ Keyboard Shortcuts

| Shortcut               | Action              | Description                                                |
|:-----------------------|:--------------------|:-----------------------------------------------------------|
| `Cmd/Ctrl + Shift + E` | **Capture & Query** | Capture current page and ask Gemini in the active session. |
| `Cmd/Ctrl + Shift + D` | **Toggle Overlay**  | Quickly show or hide the response overlay.                 |
| `Cmd/Ctrl + Shift + N` | **New Session**     | Reset the conversation and start a fresh session.          |

---

## 🏗️ Architecture

Argus is built with a modular, service-oriented architecture:

- **Frontend**: React 19 + TypeScript for a reactive and type-safe UI.
- **Build Tool**: Vite with CRXJS for a fast, modern extension development workflow.
- **State Management**: A centralized `StateManager` synchronizing Chrome Storage and in-memory state.
- **Storage Strategy**:
    - `Chrome Storage`: Small configuration and metadata.
    - `IndexedDB`: Large conversational history and image data.
- **Core Services**:
    - `GeminiAdapter`: Interface for the `@google/genai` SDK.
    - `SessionManager`: Handles CRUD operations for conversation sessions.
    - `CaptureService`: Manages screen capture and image processing.

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).
