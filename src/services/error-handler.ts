/**
 * API error handler with structured user-facing messages
 */

export async function handleApiError(error: unknown): Promise<string> {
    const msg = error instanceof Error ? error.message : String(error);
    const lower = msg.toLowerCase();
    console.error('[Argus API Error]', msg);

    if (lower.includes('api key') || lower.includes('apikey') || lower.includes('401') || lower.includes('unauthenticated')) {
        return 'Error: Invalid or missing API key. Please check your key in Argus settings.';
    }
    if (lower.includes('timeout') || lower.includes('timed out')) {
        return 'Error: Request timed out. The server took too long to respond.';
    }
    if (lower.includes('429') || lower.includes('resource_exhausted') || lower.includes('quota')) {
        return 'Error: Rate limit or quota exceeded. Please try again in a few moments.';
    }
    if (lower.includes('500') || lower.includes('502') || lower.includes('503') || lower.includes('504') || lower.includes('unavailable')) {
        return 'Error: Gemini API server is temporarily unavailable. Please retry later.';
    }
    if (lower.includes('safety') || lower.includes('blocked')) {
        return 'Error: Response was blocked due to Gemini safety policies.';
    }
    if (lower.includes('not found') || lower.includes('404')) {
        return 'Error: Selected Gemini model was not found or is deprecated.';
    }

    return `Error: ${msg}`;
}
