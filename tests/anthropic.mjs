import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
import {tool} from '../scripts/build-tools.mjs';
const {build}=await tool('esbuild/lib/main.js');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'dsh-claude-'));
try {
  const out=path.join(dir,'anthropic.cjs');
  await build({entryPoints:['runtime/providers/anthropic.ts'],outfile:out,bundle:true,platform:'node',format:'cjs',target:'node18'});
  const {anthropicRequest:request,anthropicStream:stream,thinkingConfig,isAnthropicContextError}=createRequire(import.meta.url)(out);
  assert(isAnthropicContextError(new Error('prompt is too long: 210000 tokens > 200000 maximum')));
  assert(!isAnthropicContextError(new Error('overloaded')));
  const settings={provider:'anthropic',base:'https://relay.example',reasoningEffort:'high',webSearch:false,webFetch:false};
  const text=t=>({type:'text',text:t});
  const options={model:'claude-sonnet-4-6',messages:[{role:'system',content:[text('system')]},{role:'user',content:[text('你好')]}],tools:[{name:'write_file',description:'write',parameters:{type:'object',properties:{}}},{name:'web_search',parameters:{type:'object'}}]};
  let body=request(options,settings,dir,['preference'],false);
  assert.equal(body.max_tokens,16384);assert.deepEqual(body.thinking,{type:'adaptive'});
  assert.deepEqual(body.system.map(b=>b.text),['system','preference']);assert.equal(body.tools.length,1);
  assert.equal(body.tools[0].input_schema.type,'object');assert(!('reasoning_effort' in body));
  for(const effort of ['low','high','max']) {
    const c=thinkingConfig('claude-haiku-4-5',effort);assert(c.thinking.budget_tokens>=1024);assert(c.thinking.budget_tokens<16384);
    assert.equal(thinkingConfig('claude-opus-4.7',effort).thinking.type,'adaptive');
  }
  assert.deepEqual(thinkingConfig('custom-alias','high'),{});
  assert.equal(thinkingConfig('claude-fable-5-1','none').thinking.type,'adaptive');
  assert.equal(thinkingConfig('claude-sonnet-5.5','none').thinking.type,'between_tools');
  assert(!request({...options,purpose:'session-title'},settings,dir,[],false).thinking || request({...options,purpose:'session-title'},settings,dir,[],false).thinking.type==='disabled');
  const event=(type,extra={})=>({type,...extra});
  const start=event('message_start',{message:{usage:{input_tokens:8,cache_read_input_tokens:10,cache_creation_input_tokens:2,output_tokens:1}}});
  const begin=(index,content_block)=>event('content_block_start',{index,content_block});
  const delta=(index,delta)=>event('content_block_delta',{index,delta});
  const end=index=>event('content_block_stop',{index});
  const finish=stop=>[event('message_delta',{delta:{stop_reason:stop},usage:{output_tokens:9}}),event('message_stop')];
  const native=[start,begin(0,{type:'thinking',thinking:''}),delta(0,{type:'thinking_delta',thinking:'考虑一下'}),delta(0,{type:'signature_delta',signature:'signed'}),end(0),begin(1,{type:'redacted_thinking',data:'opaque'}),end(1),begin(2,{type:'text',text:'开始'}),delta(2,{type:'text_delta',text:'处理'}),end(2),begin(3,{type:'tool_use',id:'tool-1',name:'write_file',input:{}}),delta(3,{type:'input_json_delta',partial_json:'{"path":'}),delta(3,{type:'input_json_delta',partial_json:'"报告.txt"}'}),end(3),...finish('tool_use')];
  const encode=events=>Buffer.from(': heartbeat\r\n\r\n'+events.map(e=>'event: '+e.type+'\r\ndata: '+JSON.stringify(e)+'\r\n\r\n').join(''));
  async function collect(events,split=7,signal) {
    const b=encode(events), pieces=[];for(let p=0;p<b.length;p+=split)pieces.push(b.subarray(p,p+split));
    const chunks=[];for await(const c of stream(Readable.from(pieces),settings,signal))chunks.push(c);return chunks;
  }
  const chunks=await collect(native,1); // Split inside every Chinese UTF-8 character and SSE field.
  const blocks=chunks.filter(c=>c.type==='block-end').map(c=>c.block);
  assert.equal(blocks[0].text,'考虑一下');assert.equal(blocks[1].text,'');assert.equal(blocks[2].text,'开始处理');assert.equal(JSON.parse(blocks[3].arguments).path,'报告.txt');
  assert.deepEqual(chunks.find(c=>c.type==='usage').usage,{inputTokens:20,outputTokens:9});
  const replay=chunks.at(-1).replayState;
  const assistant={role:'assistant',content:blocks,source:{kind:'model',model:options.model,replayState:JSON.parse(JSON.stringify(replay))}};
  const roundtrip={...options,messages:[...options.messages,assistant,{role:'tool',toolCallId:'tool-1',content:[text('saved')],isError:true},{role:'user',content:[text('继续')]}]};
  body=request(roundtrip,settings,dir,[],false);
  assert.deepEqual(body.messages[1].content.slice(0,2),[{type:'thinking',thinking:'考虑一下',signature:'signed'},{type:'redacted_thinking',data:'opaque'}]);
  assert.equal(body.messages[2].content[0].type,'tool_result');assert.equal(body.messages[2].content[0].is_error,true);assert.equal(body.messages[2].content[1].text,'继续');
  assert.equal(request(roundtrip,{...settings,base:'https://other.example'},dir,[],false).messages[1].content[0].type,'text');
  await assert.rejects(collect(native.slice(0,-1)),/提前结束/);
  await assert.rejects(collect([...native.slice(0,-2),event('error',{error:{message:'overloaded'}})]),/overloaded/);
  await assert.rejects(collect([start,begin(0,{type:'tool_use',id:'a',name:'write_file',input:{}}),delta(0,{type:'input_json_delta',partial_json:'{"broken":'}),end(0),...finish('tool_use')]));
  const truncated=await collect([start,begin(0,{type:'tool_use',id:'a',name:'write_file',input:{}}),delta(0,{type:'input_json_delta',partial_json:'{"broken":'}),end(0),...finish('max_tokens')]);
  assert(!truncated.some(c=>c.type==='block-end'&&c.block.type==='tool-call'));assert.equal(truncated.at(-1).reason.kind,'max-tokens');
  const abort=new AbortController();abort.abort();await assert.rejects(collect(native,5,abort.signal));
  const emptyTool=await collect([start,begin(0,{type:'tool_use',id:'a',name:'list_files',input:{}}),end(0),...finish('tool_use')]);
  assert.equal(emptyTool.find(c=>c.type==='block-end').block.arguments,'{}');
  const png=Buffer.from('89504e470d0a1a0a','hex');fs.writeFileSync(path.join(dir,'image.png'),png);
  const crypto=await import('node:crypto');const attachment={storedPath:'image.png',mediaType:'image/png',attachmentId:'sha256:'+crypto.createHash('sha256').update(png).digest('hex')};
  body=request({...options,messages:[{role:'user',content:[{type:'image',attachment}]}]},settings,dir,[],false);
  assert.equal(body.messages[0].content[0].source.data,png.toString('base64'));
  console.log('PASS: Claude request/schema, thinking modes, UTF-8 fragmented SSE, signed/redacted replay, tool round trip, truncation, errors, cancellation, image transport and route isolation');
} finally {fs.rmSync(dir,{recursive:true,force:true});}
