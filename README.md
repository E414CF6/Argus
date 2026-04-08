# Argus

Quietly powerful exam helper chrome extension with **conversational sessions**

## Overview

**Argus** is a Chrome extension that captures the current web page and queries **Gemini AI** for answers with visual
context. Now with **session-based conversations** to maintain context across multiple queries!

> Press `Cmd + Shift + E` (Mac) or `Ctrl + Shift + E` (Windows/Linux) to capture and query the current page.

## Key Features

- **Gemini AI Integration**: Powered by Gemini's vision model for accurate analysis
- **Session-Based Conversations**: Context-aware responses that remember previous exchanges
- **Full Page Capture**: Captures and analyzes entire visible page content
- **Keyboard Shortcuts**: Fast access to all features
- **Customizable Overlay**: Personalize appearance and behavior
- **Modern Architecture**: Built with TypeScript, React, and Chrome Manifest V3

## Keyboard Shortcuts

| Shortcut               | Action          | Description                               |
|------------------------|-----------------|-------------------------------------------|
| `Cmd/Ctrl + Shift + E` | Capture & Query | Capture page and query in current session |
| `Cmd/Ctrl + Shift + D` | Toggle Overlay  | Show/hide the response overlay            |
| `Cmd/Ctrl + Shift + N` | New Session     | Start a new conversation session          |

## Session System

Argus now supports **conversational sessions** that maintain context across multiple queries:

- **Automatic Context**: Queries include previous conversation history
- **Multiple Sessions**: Create and switch between different conversations
- **Persistent History**: All conversations are saved locally
- **Session Management**: View, switch, and delete sessions

### How It Works

1. First query creates a default session
2. Subsequent queries in the same session include conversation history
3. AI responses are contextually aware of previous exchanges
4. Press `Cmd/Ctrl + Shift + N` to start fresh conversation
5. Press `Cmd/Ctrl + Shift + S` to view and switch between sessions

## Architecture

### Modern Chrome Extension Structure

```
Argus/
├── src/
│   ├── background.ts          # Service worker (command handlers)
│   ├── content.ts             # Content script (overlay display)
│   ├── components/            # React components
│   │   └── SessionUI.tsx      # Session list UI
│   ├── services/              # Business logic
│   │   ├── state-manager.ts   # Central state management
│   │   ├── session-manager.ts # Session CRUD operations
│   │   ├── storage-service.ts # Chrome Storage + IndexedDB
│   │   ├── error-handler.ts   # Global error handling
│   │   ├── gemini-adapter.ts # Gemini API integration
│   │   ├── session-context.ts  # Context builder
│   │   └── capture-service.ts  # Screen capture
│   ├── types/                 # TypeScript definitions
│   │   └── messages.ts        # Message type system
│   ├── utils/                 # Shared utilities
│   │   ├── constants.ts       # Configuration constants
│   │   ├── color-utils.ts     # Color conversion
│   │   └── chrome-helpers.ts  # Chrome API wrappers
│   └── options/               # Settings page
│       ├── OptionsApp.tsx     # React settings UI
│       └── main.tsx           # Options entry point
├── manifest.json              # Extension manifest (MV3)
├── vite.config.ts            # Build configuration
└── package.json              # Dependencies

```

### Design Principles

#### 1. **Separation of Concerns**

- **Services**: Business logic (API, sessions, storage)
- **Components**: UI presentation (React)
- **Utils**: Shared helper functions
- **Types**: TypeScript type definitions

#### 2. **Singleton Pattern**

- State Manager: Central configuration store
- Session Manager: Conversation management
- Storage Service: Data persistence layer
- Error Handler: Global error management

#### 3. **Type Safety**

- Strict TypeScript throughout
- Typed message system for chrome.runtime
- Interface-based service contracts

#### 4. **Chrome MV3 Best Practices**

- Service Worker for background tasks
- IndexedDB for large data (unlimited)
- Chrome Storage for settings (5MB)
- Content Script isolation with Shadow DOM

## Technical Details

### State Management

**StateManager** serves as the central source of truth:

- Current session ID
- User settings (Gemini config, UI preferences)
- In-memory caching for performance
- Synchronized with Chrome Storage

### Session Storage

**Dual-layer storage strategy**:

- **Chrome Storage**: Settings, current session ID (fast, small data)
- **IndexedDB**: Session metadata, conversation history (unlimited, large data)

### Error Handling

**Centralized error management**:

- Severity levels (INFO, WARNING, ERROR, CRITICAL)
- Context-aware error messages
- User-friendly notifications
- Detailed console logging for debugging

### Gemini Integration

**Official SDK Integration**:

- Uses `@google/genai` official TypeScript SDK
- Vision model support (image + text + page context)
- Multimodal content handling
- Automatic conversation history formatting
- Built-in timeout handling (30s)
- Type-safe API interactions

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) file for details.
