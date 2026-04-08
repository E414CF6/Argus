/**
 * Message types for background/content script communication
 */

export type MessageType = 'displayResult' | 'toggleOverlay' | 'error';

export interface DisplayResultMessage {
    type: 'displayResult';
    text: string;
}

export interface ToggleOverlayMessage {
    type: 'toggleOverlay';
}

export interface ErrorMessage {
    type: 'error';
    error: string;
}

export type ChromeMessage = DisplayResultMessage | ToggleOverlayMessage | ErrorMessage;

export const MessageFactory = {
    displayResult: (text: string): DisplayResultMessage => ({type: 'displayResult', text}),
    toggleOverlay: (): ToggleOverlayMessage => ({type: 'toggleOverlay'}),
    error: (error: string): ErrorMessage => ({type: 'error', error}),
};
