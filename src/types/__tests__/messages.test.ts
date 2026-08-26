import {describe, expect, it} from 'vitest';
import {MessageFactory} from '../messages';

describe('MessageFactory', () => {
    it('creates displayResult message', () => {
        const msg = MessageFactory.displayResult('hello', 'success');
        expect(msg).toEqual({type: 'displayResult', text: 'hello', status: 'success'});
    });

    it('creates toggleOverlay message', () => {
        const msg = MessageFactory.toggleOverlay();
        expect(msg).toEqual({type: 'toggleOverlay'});
    });

    it('creates error message', () => {
        const msg = MessageFactory.error('something failed');
        expect(msg).toEqual({type: 'error', error: 'something failed'});
    });

    it('creates clearOverlay and ping messages', () => {
        expect(MessageFactory.clearOverlay()).toEqual({type: 'clearOverlay'});
        expect(MessageFactory.ping()).toEqual({type: 'PING'});
    });
});
