// Official rolling alias: DeepSeek resolves this to its current Flash vision model.
export const latestVisionModel = 'deepseek-flash';
export function supportsImages(model: string, models: any[] = []) {
  const entry=models.find(m=>m?.id===model);
  if(Array.isArray(entry?.input_modalities))return entry.input_modalities.includes('image');
  if (/^claude-(?:3|sonnet-|opus-|haiku-|fable-|mythos-)/i.test(model)) return true;
  return /^(deepseek-flash|deepseek-v4(?:\.1)?-flash(?:-vision-exp)?)$/i.test(model);
}

// Never send a different provider's model ID or credentials to an image route.
export function imageFallback(settings: any) {
  if (settings.autoImageModel === false) return undefined;
  if (settings.provider !== 'anthropic') return !Array.isArray(settings.models) || settings.models.some(m => m.id === latestVisionModel) ? latestVisionModel : settings.models.find(m => supportsImages(m.id,settings.models))?.id;
  return settings.models?.find(m => supportsImages(m.id,settings.models))?.id;
}
