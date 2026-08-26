/**
 * Message types for background/content script communication
 */

export type MessageType = 'displayResult' | 'toggleOverlay' | 'error' | 'clearOverlay';

export interface DisplayResultMessage {
    type: 'displayResult';
    text: string;
    status?: 'loading' | 'success' | 'error' | 'info';
}

export interface ToggleOverlayMessage {
    type: 'toggleOverlay';
}

export interface ErrorMessage {
    type: 'error';
    error: string;
}

export interface ClearOverlayMessage {
    type: 'clearOverlay';
}

export interface PingMessage {
    type: 'PING';
}

export type ChromeMessage =
    | DisplayResultMessage
    | ToggleOverlayMessage
    | ErrorMessage
    | ClearOverlayMessage
    | PingMessage;

export const MessageFactory = {
    displayResult: (text: string, status?: 'loading' | 'success' | 'error' | 'info'): DisplayResultMessage => ({
        type: 'displayResult', text, status,
    }),
    toggleOverlay: (): ToggleOverlayMessage => ({type: 'toggleOverlay'}),
    error: (error: string): ErrorMessage => ({type: 'error', error}),
    clearOverlay: (): ClearOverlayMessage => ({type: 'clearOverlay'}),
    ping: (): PingMessage => ({type: 'PING'}),
};
