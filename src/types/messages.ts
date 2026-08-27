/**
 * Message types for background/content script communication
 */

export type MessageType =
    | 'displayResult'
    | 'toggleOverlay'
    | 'error'
    | 'clearOverlay'
    | 'REQUEST_CAPTURE'
    | 'REQUEST_TOGGLE_OVERLAY'
    | 'REQUEST_NEW_SESSION'
    | 'PING';

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

export interface RequestCaptureMessage {
    type: 'REQUEST_CAPTURE';
}

export interface RequestToggleOverlayMessage {
    type: 'REQUEST_TOGGLE_OVERLAY';
}

export interface RequestNewSessionMessage {
    type: 'REQUEST_NEW_SESSION';
}

export interface PingMessage {
    type: 'PING';
}

export type ChromeMessage =
    | DisplayResultMessage
    | ToggleOverlayMessage
    | ErrorMessage
    | ClearOverlayMessage
    | RequestCaptureMessage
    | RequestToggleOverlayMessage
    | RequestNewSessionMessage
    | PingMessage;

export const MessageFactory = {
    displayResult: (text: string, status?: 'loading' | 'success' | 'error' | 'info'): DisplayResultMessage => ({
        type: 'displayResult', text, status,
    }),
    toggleOverlay: (): ToggleOverlayMessage => ({type: 'toggleOverlay'}),
    error: (error: string): ErrorMessage => ({type: 'error', error}),
    clearOverlay: (): ClearOverlayMessage => ({type: 'clearOverlay'}),
    ping: (): PingMessage => ({type: 'PING'}),
    requestCapture: (): RequestCaptureMessage => ({type: 'REQUEST_CAPTURE'}),
    requestToggleOverlay: (): RequestToggleOverlayMessage => ({type: 'REQUEST_TOGGLE_OVERLAY'}),
    requestNewSession: (): RequestNewSessionMessage => ({type: 'REQUEST_NEW_SESSION'}),
};
