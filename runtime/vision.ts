// Official rolling alias: DeepSeek resolves this to its current Flash vision model.
export const latestVisionModel = 'deepseek-flash';
export function supportsImages(model: string, models: any[] = []) {
  const entry=models.find(m=>m?.id===model);
  if(Array.isArray(entry?.input_modalities))return entry.input_modalities.includes('image');
  return /^(deepseek-flash|deepseek-v4(?:\.1)?-flash(?:-vision-exp)?)$/i.test(model);
}
