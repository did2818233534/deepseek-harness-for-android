import { StringDecoder } from 'node:string_decoder';
import { attachmentImage } from '../files/attachments.ts';
import { supportsImages } from './vision.ts';

export function isAnthropicContextError(error: unknown) {
  const message=error instanceof Error ? error.message : String(error);
  return /prompt is too long|input length and max_tokens exceed context limit/i.test(message);
}

// Native Messages transport. Canonical history stays untouched; only this boundary
// knows about tool_result blocks and signed thinking replay.
export function thinkingConfig(model: string, effort: string) {
  const id = model.toLowerCase().replace(/\./g, '-');
  const adaptive = /^claude-(?:(?:opus|sonnet)-4-[6-9]|(?:opus|sonnet|haiku|fable|mythos)-5|mythos-preview)/.test(id);
  const always = /^claude-(?:(?:fable|mythos)-|opus-5-5)/.test(id);
  if (adaptive) {
    const off = effort === 'none';
    return {
      thinking: {type: off && !always ? (id.startsWith('claude-sonnet-5-5') ? 'between_tools' : 'disabled') : 'adaptive'},
      output_config: {effort: off ? 'low' : effort === 'max' ? (/^claude-opus-(?:4-[6-9]|5)/.test(id) ? 'max' : 'high') : effort},
    };
  }
  // Unknown relay aliases and pre-thinking Claude models keep provider defaults.
  if (!/^claude-(?:(?:sonnet|opus|haiku)-4|3-7-sonnet)/.test(id) || effort === 'none') return {};
  return {thinking: {type:'enabled', budget_tokens: effort === 'low' ? 1024 : effort === 'max' ? 8192 : 4096}};
}

export function anthropicRequest(options, settings, workspace: string, systemExtras: string[], fast: boolean) {
  const system: any[] = options.system ? [{type:'text',text:options.system}] : [];
  const messages: any[] = [];
  const images = supportsImages(options.model, settings.models);
  for (const message of options.messages) {
    if (message.role === 'system' || message.role === 'developer') {
      for (const block of message.content) if (block.type === 'text' && block.text) system.push({type:'text',text:block.text});
      continue;
    }
    const role = message.role === 'assistant' ? 'assistant' : 'user';
    let content: any[] = [];
    if (message.role === 'tool') {
      content = [{type:'tool_result',tool_use_id:message.toolCallId,
        content:message.content.filter(b=>b.type==='text').map(b=>b.text).join('\n'),
        ...(message.isError ? {is_error:true} : {})}];
    } else {
      const replay = message.source?.replayState;
      const sameRoute = replay?.response?.protocol === 'anthropic' && replay.response.base === settings.base && message.source?.model === options.model;
      message.content.forEach((block, position) => {
        if (block.type === 'text' && block.text) content.push({type:'text',text:block.text});
        if (block.type === 'image') {
          if (images && !block.offloaded) {
            const url = attachmentImage(workspace,block.attachment).image_url.url;
            const match = /^data:([^;]+);base64,(.*)$/.exec(url)!;
            content.push({type:'image',source:{type:'base64',media_type:match[1],data:match[2]}});
          } else content.push({type:'text',text:'[此前发送的图片，本轮未附带]'});
        }
        if (role === 'assistant' && block.type === 'tool-call')
          content.push({type:'tool_use',id:block.id,name:block.name,input:JSON.parse(block.arguments)});
        const native = sameRoute ? replay.blocks?.[position] : undefined;
        if (role === 'assistant' && block.type === 'reasoning' && native) {
          if (native.type === 'thinking' && native.thinking === block.text && native.signature)
            content.push({type:'thinking',thinking:native.thinking,signature:native.signature});
          else if (native.type === 'redacted_thinking' && !block.text && native.data)
            content.push({type:'redacted_thinking',data:native.data});
        }
      });
    }
    if (!content.length) continue;
    if (messages.at(-1)?.role === role) messages.at(-1).content.push(...content);
    else messages.push({role,content});
  }
  for (const text of systemExtras) if(text) system.push({type:'text',text});
  if (fast) {
    const text={type:'text',text:'请根据原问题及已经取得的工具结果直接给出回答；资料不足时请明确说明，避免重复执行已经完成的操作。'};
    if (messages.at(-1)?.role==='user') messages.at(-1).content.push(text);
    else messages.push({role:'user',content:[text]});
  }
  const effort = fast || options.purpose === 'session-title' ? 'none' : (settings.reasoningEffort || 'high');
  const tools = (options.tools || []).filter(t=>!(t.name==='web_search' && settings.webSearch===false) && !(t.name==='web_fetch' && settings.webFetch===false))
    .map(t=>({name:t.name,description:t.description,input_schema:t.parameters}));
  return {model:options.model,stream:true,max_tokens:effort==='none'?8192:16384,messages,
    ...(system.length ? {system} : {}), ...(tools.length ? {tools} : {}), ...thinkingConfig(options.model,effort)};
}

async function* events(body) {
  const decoder = new StringDecoder('utf8');
  let pending = '', data: string[] = [];
  function line(value: string) {
    if (value.endsWith('\r')) value=value.slice(0,-1);
    if (!value) { const result=data.join('\n'); data=[]; return result || undefined; }
    if (value.startsWith('data:')) data.push(value.slice(5).replace(/^ /,''));
  }
  for await (const raw of body) {
    pending += decoder.write(Buffer.from(raw));
    let end;
    while ((end=pending.indexOf('\n'))>=0) {
      const event=line(pending.slice(0,end));pending=pending.slice(end+1);
      if(event) yield JSON.parse(event);
    }
  }
  pending += decoder.end();
  if(pending) line(pending);
  const last=line(''); if(last) yield JSON.parse(last);
}

export async function* anthropicStream(body, settings, signal?) {
  const blocks = new Map<number, any>();
  let usage: any = {}, stop = '', complete = false, began = false;
  for await (const event of events(body)) {
    signal?.throwIfAborted();
    if (event.type === 'error' || event.error) throw Error(event.error?.message || 'Claude 流式响应错误');
    if (event.type === 'message_start') { began=true; usage={...event.message?.usage}; }
    if (event.type === 'content_block_start') {
      const native=event.content_block, index=event.index;
      if (!Number.isInteger(index) || index<0 || blocks.has(index)) throw Error('Claude 返回了无效的内容块');
      if (!['text','thinking','redacted_thinking','tool_use'].includes(native?.type)) throw Error('暂不支持此 Claude 内容块：'+native?.type);
      const type=native.type==='tool_use'?'tool-call':native.type==='text'?'text':'reasoning';
      blocks.set(index,{native:{...native},type,args:'',closed:false});
      yield {type:'block-start',index,blockType:type};
      if (native.type==='text' && native.text) yield {type:'text-delta',index,text:native.text};
      if (native.type==='thinking' && native.thinking) yield {type:'reasoning-delta',index,text:native.thinking};
      if (native.type==='tool_use') yield {type:'tool-call-delta',index,id:native.id,name:native.name,argumentsDelta:''};
    }
    if (event.type === 'content_block_delta') {
      const block=blocks.get(event.index), delta=event.delta;
      if(!block || block.closed) throw Error('Claude 内容块顺序异常');
      if(delta.type==='text_delta' && block.type==='text') {
        block.native.text=(block.native.text||'')+delta.text;
        yield {type:'text-delta',index:event.index,text:delta.text};
      } else if(delta.type==='thinking_delta' && block.native.type==='thinking') {
        block.native.thinking=(block.native.thinking||'')+delta.thinking;
        yield {type:'reasoning-delta',index:event.index,text:delta.thinking};
      } else if(delta.type==='signature_delta') block.native.signature=(block.native.signature||'')+delta.signature;
      else if(delta.type==='input_json_delta' && block.type==='tool-call') {
        block.args+=delta.partial_json;
        yield {type:'tool-call-delta',index:event.index,id:block.native.id,argumentsDelta:delta.partial_json};
      }
    }
    if (event.type === 'content_block_stop') {
      const block=blocks.get(event.index); if(!block || block.closed) throw Error('Claude 内容块顺序异常');
      block.closed=true;
    }
    if (event.type === 'message_delta') { stop=event.delta?.stop_reason || stop; usage={...usage,...event.usage}; }
    if (event.type === 'message_stop') {complete=true; break;}
  }
  signal?.throwIfAborted();
  if(!began || !complete || !stop || [...blocks.values()].some(b=>!b.closed))
    throw Error('模型连接提前结束，请重试；未执行不完整的工具调用。');
  if(!['end_turn','stop_sequence','tool_use','max_tokens','refusal'].includes(stop))
    throw Error('Claude 请求未正常结束：'+stop);
  const nativeBlocks: any[]=[];
  for (const [index,b] of blocks) {
    const n=b.native; let block;
    if(b.type==='tool-call') {
      if(stop==='max_tokens') { nativeBlocks.push(null); continue; }
      if(stop!=='tool_use' || !n.id || !n.name) throw Error('Claude 返回了不完整的工具调用');
      const input=b.args ? JSON.parse(b.args) : n.input;
      if(!input || typeof input!=='object' || Array.isArray(input)) throw Error('Claude 工具参数必须是 JSON 对象');
      block={type:'tool-call',id:n.id,name:n.name,arguments:JSON.stringify(input)};
    } else block={type:b.type,text:b.type==='text'?(n.text||''):(n.thinking||'')};
    nativeBlocks.push(b.type==='reasoning' ? n : null);
    yield {type:'block-end',index,block};
  }
  if(stop==='tool_use' && ![...blocks.values()].some(b=>b.type==='tool-call')) throw Error('Claude 未返回工具调用内容');
  yield {type:'usage',usage:{inputTokens:(usage.input_tokens||0)+(usage.cache_read_input_tokens||0)+(usage.cache_creation_input_tokens||0),outputTokens:usage.output_tokens||0}};
  yield {type:'finish',reason:{kind:stop==='max_tokens'?'max-tokens':stop==='tool_use'?'tool-calls':'stop'},
    replayState:{response:{protocol:'anthropic',base:settings.base},blocks:nativeBlocks}};
}
