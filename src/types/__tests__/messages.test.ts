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

    it('creates displayStreamChunk and requestCustomQuery messages', () => {
        expect(MessageFactory.displayStreamChunk('abc', 'full text abc')).toEqual({
            type: 'displayStreamChunk',
            chunk: 'abc',
            fullText: 'full text abc'
        });
        expect(MessageFactory.requestCustomQuery('What is 2+2?')).toEqual({
            type: 'REQUEST_CUSTOM_QUERY',
            prompt: 'What is 2+2?'
        });
    });
});
