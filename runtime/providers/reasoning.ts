import rules from '../../app/src/main/assets/reasoning-models.json';
export function reasoningOptions(provider:string,model:string) {
  const id=model.toLowerCase().replace(/\./g,'-');
  const row=rules.find(r=>r.provider===provider&&new RegExp(r.pattern).test(id));
  return row?{...row,default:/^claude-(opus|haiku)-5-5/.test(id)?'medium':row.default}:undefined;
}
export function nativeThinking(provider:string,model:string,effort:string) {
  const row=reasoningOptions(provider,model);
  if(!row || effort==='default')return {};
  // Fast timeout recovery chooses the lowest valid setting on always-on models.
  const value=row.levels.includes(effort)?effort:effort==='none'?row.levels[0]:row.default;
  if(row.mode==='deepseek')return {thinking:{type:value==='none'?'disabled':'enabled'},...(value==='none'?{}:{reasoning_effort:value})};
  if(row.mode==='adaptive')return {thinking:{type:value==='none'?(row.off||'disabled'):'adaptive'},output_config:{effort:value==='none'?'low':value}};
  if(value==='none')return {thinking:{type:'disabled'}};
  const budget=value==='low'?1024:value==='max'?8192:4096;
  return {thinking:{type:'enabled',budget_tokens:budget},...(row.mode==='manual_effort'?{output_config:{effort:value}}:{})};
}
/** Only a rejected optional parameter may be retried, before receiving a stream. */
export function thinkingRejected(error:unknown) {
  const text=error instanceof Error?error.message:String(error);
  const status=text.match(/(?:API|HTTP)\s+(\d{3})/i);
  if(status && !["400","422"].includes(status[1]))return false;
  return /(?:400|422|invalid_request|unsupported|unknown|not supported|not permitted)/i.test(text)
    && /thinking|reasoning_effort|output_config|effort|budget_tokens/i.test(text)
    && !/context|prompt is too long|token limit|api.?key|auth/i.test(text);
}
