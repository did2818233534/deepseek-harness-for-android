import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
import assert from 'node:assert/strict';
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'dsh-claude-runtime-'));
const root=path.join(dir,'runtime');fs.mkdirSync(root);fs.mkdirSync(path.join(dir,'workspace'));
const token='test-local-token';let child,requests=[],tools=[],mode='tool',call=0,log='';
const settings={provider:'anthropic',base:'https://relay.example',model:'claude-sonnet-4-6',hasKey:true,reasoningEffort:'high',maxSteps:8,webSearch:false,webFetch:false,autoImageModel:true};
const emit=(res,type,extra={})=>res.write('event: '+type+'\ndata: '+JSON.stringify({type,...extra})+'\n\n');
function response(res,blocks,reason='end_turn') {
  emit(res,'message_start',{message:{usage:{input_tokens:20,output_tokens:1}}});
  blocks.forEach((b,index)=>{emit(res,'content_block_start',{index,content_block:b});emit(res,'content_block_stop',{index});});
  emit(res,'message_delta',{delta:{stop_reason:reason},usage:{output_tokens:12}});emit(res,'message_stop');res.end();
}
const bridge=http.createServer(async(req,res)=>{
  try {
    assert.equal(req.headers.authorization,'Bearer '+token);
    let raw='';for await(const b of req)raw+=b;const data=JSON.parse(raw);
    if(req.url==='/settings'){res.end(JSON.stringify(settings));return;}
    if(['/status','/cancel-model','/cancel-network','/model-switch'].includes(req.url)){res.end('{}');return;}
    if(req.url==='/tool'){tools.push(data);res.end(JSON.stringify({path:'报告.txt',bytes:6}));return;}
    assert.equal(req.url,'/model');assert.equal(data.androidProvider,'anthropic');assert.equal(data.androidBase,settings.base);
    assert(data.system);assert.equal(data.stream,true);assert(data.max_tokens>0);
    assert(!data.messages.some(m=>m.role==='system'||m.role==='tool'));
    res.setHeader('content-type','text/event-stream');
    if(data.system.some(b=>b.text.startsWith('为对话生成简短标题'))){response(res,[{type:'text',text:'Claude 文件测试'}]);return;}
    requests.push(data);
    if(mode==='unsupported-thinking' && data.thinking){res.statusCode=400;res.end('invalid_request: thinking and output_config not supported');return;}
    if(mode==='broken'){emit(res,'message_start',{message:{usage:{}}});emit(res,'content_block_start',{index:0,content_block:{type:'tool_use',id:'broken',name:'write_file',input:{}}});res.end();return;}
    if(mode==='hold'){emit(res,'message_start',{message:{usage:{}}});emit(res,'content_block_start',{index:0,content_block:{type:'text',text:'等待中'}});return;}
    if(mode==='tool'&&call++===0){response(res,[{type:'thinking',thinking:'先写文件',signature:'test-signature'},{type:'tool_use',id:'write-1',name:'write_file',input:{path:'报告.txt',content:'你好'}}],'tool_use');return;}
    response(res,[{type:'text',text:'已完成 Claude 测试'}]);
  } catch(e) {log+=e.stack;res.statusCode=500;res.end(String(e));}
});
await new Promise(r=>bridge.listen(0,'127.0.0.1',r));
async function until(fn){for(let i=0;i<400;i++){const value=await fn();if(value)return value;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out '+log);}
async function start(){fs.rmSync(path.join(root,'runtime-port'),{force:true});child=spawn(process.execPath,['--require',path.resolve('tests/legacy-node.cjs'),'app/src/main/assets/runtime/main.cjs',root,String(bridge.address().port),token],{stdio:['ignore','pipe','pipe']});child.stdout.on('data',b=>log+=b);child.stderr.on('data',b=>log+=b);await until(()=>{if(child.exitCode!==null)throw Error(log);return fs.existsSync(path.join(root,'runtime-port'));});}
async function stop(){if(child&&child.exitCode===null){child.kill();await new Promise(r=>child.once('exit',r));}}
async function api(route,data={}){const port=fs.readFileSync(path.join(root,'runtime-port'),'utf8');const r=await fetch('http://127.0.0.1:'+port+route,{method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify(data)});const b=await r.json();assert.equal(r.status,200,JSON.stringify(b));return b;}
async function settled(){return until(async()=>{const s=await api('/state');return !s.busy?s:null;});}
try {
  await start();await api('/send',{text:'创建报告'});let state=await settled();
  assert.equal(state.lastError,'');assert.equal(tools.length,1);assert.equal(tools[0].name,'write_file');
  assert(requests[1].messages.some(m=>m.role==='assistant'&&m.content.some(b=>b.type==='thinking'&&b.signature==='test-signature')),'signed thinking survives harness assembly');
  assert(requests[1].messages.some(m=>m.role==='user'&&m.content.some(b=>b.type==='tool_result'&&b.tool_use_id==='write-1')));
  assert(!requests[1].tools.some(t=>t.name==='web_search'));
  const session=state.id;mode='text';await stop();await start();await api('/select',{id:session});await api('/send',{text:'继续'});state=await settled();assert.equal(state.lastError,'');
  assert(requests.at(-1).messages.some(m=>m.content.some(b=>b.type==='thinking'&&b.signature==='test-signature')),'signed thinking survives process restart');
  for(let i=0;i<2;i++){mode='broken';await api('/send',{text:'断流'});state=await settled();assert(state.lastError.includes('提前结束'));assert.equal(tools.length,1);mode='text';await api('/send',{text:'重试'});state=await settled();assert.equal(state.lastError,'');}
  mode='hold';await api('/send',{text:'停止测试'});await until(()=>requests.at(-1)?.messages.at(-1)?.content.some(b=>b.text==='停止测试'));await api('/cancel');state=await settled();
  mode='text';await api('/send',{text:'取消后继续'});state=await settled();assert.equal(state.lastError,'');assert.equal(tools.length,1);
  mode='unsupported-thinking';
  for(let n=0;n<2;n++) {
    const before=requests.length;await api('/send',{text:'中转站忽略不支持的思考设置'});state=await settled();
    assert.equal(state.lastError,'');assert.equal(requests.length,before+2);
    assert(requests.at(-2).thinking);assert(!requests.at(-1).thinking);assert(!requests.at(-1).output_config);
  }
  console.log('PASS: Claude through full harness loop, tool execution/replay, signed thinking restart, broken-stream retry twice, cancellation and recovery');
} finally {await stop();bridge.closeAllConnections();await new Promise(r=>bridge.close(r));fs.rmSync(dir,{recursive:true,force:true});}
