/**
 * Simple error handler
 */

export async function handleApiError(error: any): Promise<string> {
    const msg = error?.message || String(error);
    console.error('[API Error]', msg);

    if (msg.includes('API key')) return 'Error: Invalid API key';
    if (msg.includes('timeout')) return 'Error: Request timed out';
    if (msg.includes('429')) return 'Error: Rate limit exceeded';
    if (msg.includes('500') || msg.includes('502') || msg.includes('503')) return 'Error: API server error';

    return `Error: ${msg}`;
}
