// Android transport seam for the unmodified upstream DeepSeek API providers.
import { WebError } from '@deepseek-ai/dsh-web';
let dispatch: any;
export function configureWebTransport(fn: any) { dispatch = fn; }
export function isRecord(value: any) { return !!value && typeof value === 'object' && !Array.isArray(value); }
export function invalidResponse() { return new WebError('DeepSeek returned an invalid response body', 'WEB_PROVIDER_ERROR'); }
export function hasDeepSeekWebCredential(options: any) { return Boolean(options.resolveApiKey); }
export async function dispatchDeepSeekWeb(options: any, request: any, signal: any) {
  try {
    return request.decode(await dispatch('/web-api', { endpoint: request.endpoint, body: request.body }, signal));
  } catch (error: any) {
    if (error instanceof WebError) throw error;
    throw new WebError(signal?.aborted ? 'DeepSeek web request aborted' : error.message, signal?.aborted ? 'WEB_ABORTED' : 'WEB_PROVIDER_ERROR', { cause: error });
  }
}
