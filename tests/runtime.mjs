import http from "node:http";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import assert from "node:assert/strict";
const testHome = fs.mkdtempSync(path.join(os.tmpdir(), "dsh-android-test-"));
const root = path.join(testHome, "runtime");
fs.mkdirSync(root);
const workspace = path.join(testHome, "workspace");
fs.mkdirSync(workspace);
const token = "test-token";
let modelName = "test-model", reasoningEffort;
let webEnabled = true, webRequests = [], invalidWeb = false;
let titleRequests=[], titleMode="ok";
let largeTool = false;
let overflowBudget = 0, summaryCalls = 0, summaryMode = "ok";
let child,
  requests = [],
  toolCalls = [],
  mode = "tool",
  calls = 0;
const bridge = http.createServer(async (req, res) => {
  assert.equal(req.headers.authorization, "Bearer " + token);
  req.setEncoding("utf8");
  let raw = "";
  for await (const b of req) raw += b;
  const b = JSON.parse(raw);
  res.setHeader("content-type", "application/json");
  if (req.url === "/settings") {
    res.end(
      JSON.stringify({
        model: modelName,
        hasKey: true,
        maxSteps: 32,
        thinking: false,
        reasoningEffort,
        webSearch: webEnabled, webFetch: webEnabled,
      }),
    );
    return;
  }
  if (req.url === "/status" || req.url === "/cancel-network") {
    res.end("{}");
    return;
  }
  if (req.url === "/tool") {
    toolCalls.push(b);
    await new Promise(r => setTimeout(r, 180));
    res.end(JSON.stringify({ path: "answer.txt", bytes: 5, ...(largeTool ? {content:"工具原始结果。".repeat(5000)} : {}) }));
    return;
  }
  if (req.url === "/web-api") {
    webRequests.push(b);
    res.end(JSON.stringify(invalidWeb ? {unexpected:true} : b.endpoint.endsWith('/search') ? {results: [{url:'https://example.com/source',title:'Source',snippet:'Verified snippet',date:null}]} : {content:'# Retrieved page\nVerified page body'}));
    return;
  }
  if (req.url === "/model") {
    if(b.messages?.[0]?.content?.startsWith('为对话生成简短标题')) {
      titleRequests.push(b);
      if(titleMode==='fail'){res.statusCode=500;res.end('title unavailable');return;}
      if(titleMode==='hold'){await new Promise(r=>setTimeout(r,400));}
      res.end('data: '+JSON.stringify({choices:[{delta:{content:'文件整理与内容分析'},finish_reason:'stop'}]})+'\n\ndata: [DONE]\n\n');return;
    }
    requests.push(b);
    res.setHeader("content-type", "text/event-stream");
    if (mode.startsWith('compact')) {
      const summary = typeof b.messages.at(-1)?.content === 'string' && b.messages.at(-1).content.startsWith('You are now acting as a compaction engine');
      const emit = (text, usage) => res.end('data: '+JSON.stringify({choices:[{delta:{content:text},finish_reason:'stop'}], ...(usage ? {usage:{prompt_tokens:usage,completion_tokens:10}} : {})})+'\n\ndata: [DONE]\n\n');
      if (summary) {
        summaryCalls++;
        if (summaryMode === 'fail') { res.statusCode=500;res.end('summary unavailable');return; }
        if (summaryMode === 'hang') { res.write('data: '+JSON.stringify({choices:[{delta:{content:'summary draft'}}]})+'\n\n');return; }
        emit('## Primary Request and Intent\n- Preserve the user task and continue.');return;
      }
      if (overflowBudget > 0) {overflowBudget--;res.statusCode=400;res.end(JSON.stringify({error:{code:'context_length_exceeded',message:'maximum context length exceeded'}}));return;}
      emit('继续处理完成',mode==='compact-pressure' ? 900000 : undefined);return;
    }
    if (mode === 'length' || mode === 'length-tool') {
      if(mode==='length-tool')res.write('data: '+JSON.stringify({choices:[{delta:{tool_calls:[{index:0,id:'cutoff',function:{name:'write_file',arguments:'{\"path\":'}}]}}]})+'\n\n');
      res.write('data: '+JSON.stringify({choices:[{delta:{reasoning_content:'实际思考片段'},finish_reason:null}]})+'\n\n');
      res.end('data: '+JSON.stringify({choices:[{delta:{},finish_reason:'length'}]})+'\n\ndata: [DONE]\n\n');return;
    }
    if (mode === "streaming") {
      const send = d => res.write("data: " + JSON.stringify({choices:[{delta:d}]}) + "\n\n");
      send({reasoning_content:'先核对'});
      await new Promise(r => setTimeout(r, 180));
      send({reasoning_content:'事实。'});
      await new Promise(r => setTimeout(r, 180));
      send({content:'你好'});
      await new Promise(r => setTimeout(r, 220));
      send({content:'，世界！'});
      res.end('data: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n');
      return;
    }
    if (mode === 'hang-reasoning' || mode === 'fail-stream') {
      res.write('data: ' + JSON.stringify({choices:[{delta:{reasoning_content:'已收到的真实思考'}}]}) + '\n\n');
      await new Promise(r => setTimeout(r, 120));
      res.write('data: ' + JSON.stringify({choices:[{delta:{content:'未完成的正文'}}]}) + '\n\n');
      if (mode === 'fail-stream') { await new Promise(r => setTimeout(r, 100)); res.end(); }
      return;
    }
    if (mode === "hang") {
      res.write(
        "data: " +
          JSON.stringify({ choices: [{ delta: { content: "partial" } }] }) +
          "\n\n",
      );
      return;
    }
    const delta =
      ["tool", "search", "fetch"].includes(mode) && calls++ === 0
        ? {
            tool_calls: [
              {
                index: 0,
                id: "call_1",
                function: {
                  name: mode === "search" ? "web_search" : mode === "fetch" ? "web_fetch" : "write_file",
                  arguments: JSON.stringify(mode === "search" ? {queries:["alpha", "beta", "alpha"]} : mode === "fetch" ? {url:"https://example.com/source"} : {path:"answer.txt",content:"hello"}),
                },
              },
            ],
          }
        : { content: mode === "unicode" ? "中文跨块测试：你好，世界！👋😀 café 𠮷" : "已生成 answer.txt" };
    const event = "data: " + JSON.stringify({ choices: [{ delta }] }) + "\n\n";
    if (mode === 'unicode') {
      for (const byte of Buffer.from(event)) { res.write(Buffer.from([byte])); await new Promise(r => setTimeout(r, 1)); }
    } else if (delta.tool_calls) {
      const call = delta.tool_calls[0];
      const argumentsText = call.function.arguments;
      res.write('data: ' + JSON.stringify({choices:[{delta:{tool_calls:[{...call,function:{name:call.function.name,arguments:argumentsText.slice(0,5)}}]}}]}) + '\n\n');
      await new Promise(r => setTimeout(r, 80));
      res.write('data: ' + JSON.stringify({choices:[{delta:{tool_calls:[{index:0,function:{arguments:argumentsText.slice(5)}}]}}]}) + '\n\n');
    } else res.write(event);
    res.write(
      "data: " +
        JSON.stringify({
          choices: [
            {
              delta: {},
              finish_reason: delta.tool_calls ? "tool_calls" : "stop",
            },
          ],
        }) +
        "\n\n",
    );
    res.end("data: [DONE]\n\n");
    return;
  }
  res.writeHead(404);
  res.end("{}");
});
await new Promise((r) => bridge.listen(0, "127.0.0.1", r));
async function until(fn) {
  for (let i = 0; i < 200; i++) {
    const v = await fn();
    if (v) return v;
    await new Promise((r) => setTimeout(r, 30));
  }
  throw Error("Timed out");
}
async function start() {
  fs.rmSync(path.join(root, "runtime-port"), { force: true });
  child = spawn(
    process.env.TEST_NODE || process.execPath,
    [
      "app/src/main/assets/runtime/main.cjs",
      root,
      String(bridge.address().port),
      token,
    ],
    { stdio: ["ignore", "pipe", "pipe"] },
  );
  let log = "";
  child.stdout.on("data", (x) => (log += x));
  child.stderr.on("data", (x) => (log += x));
  await until(() => {
    if (child.exitCode !== null) throw Error(log);
    if (fs.existsSync(path.join(root, "runtime-error")))
      throw Error(fs.readFileSync(path.join(root, "runtime-error"), "utf8"));
    return fs.existsSync(path.join(root, "runtime-port"));
  });
}
async function api(route, data = {}) {
  const p = fs.readFileSync(path.join(root, "runtime-port"), "utf8");
  const r = await fetch("http://127.0.0.1:" + p + route, {
    method: "POST",
    headers: { authorization: "Bearer " + token },
    body: JSON.stringify(data),
  });
  const b = await r.json();
  assert.equal(r.status, 200, JSON.stringify(b));
  return b;
}
async function fragmentedSend(text) {
  const port = Number(fs.readFileSync(path.join(root, "runtime-port"), "utf8"));
  const body = Buffer.from(JSON.stringify({text}));
  await new Promise((resolve, reject) => {
    const req = http.request({hostname:'127.0.0.1', port, path:'/send', method:'POST',
      headers:{authorization:'Bearer '+token, 'content-type':'application/json; charset=utf-8', 'content-length':body.length}}, res => {
        res.resume(); res.on('end', () => res.statusCode === 200 ? resolve() : reject(Error('Fragmented send failed')));
      });
    req.on('error', reject);
    void (async () => {
      for (const byte of body) { req.write(Buffer.from([byte])); await new Promise(r => setTimeout(r, 2)); }
      req.end();
    })().catch(reject);
  });
}
async function stop() {
  if (child && child.exitCode === null) {
    child.kill("SIGTERM");
    await new Promise((r) => child.once("exit", r));
  }
}
try {
  await start();
  assert.equal((await api("/sessions")).sessions.length, 0, "empty chat hidden");
  await stop(); await start();
  assert.equal((await api("/sessions")).sessions.length, 0, "empty chat hidden after restart");
  await api("/send", { text: "创建一个文件" });
  const runningTool = await until(async () => {
    const s = await api('/state'); const t = s.turns.at(-1);
    return t?.tools.some(tool => tool.status === 'running') ? t : null;
  });
  assert(runningTool.busy && runningTool.processActive, 'tool execution is visible before completion');
  let state = await until(async () => {
    const s = await api("/state");
    return !s.busy && s.events.some((e) => e.type === "assistant/message")
      ? s
      : null;
  });
  assert.equal(toolCalls.length, 1);
  assert.equal(toolCalls[0].name, "write_file");
  assert.equal(requests.length, 2);
  assert(
    requests[1].messages.some(
      (m) => m.role === "tool" && m.tool_call_id === "call_1",
    ),
  );
  assert(state.events.some((e) => e.type === "tool/result"));
  assert.equal(state.turns[0].tools[0].name, 'write_file');
  assert.equal(state.turns[0].tools[0].status, 'completed');
  assert.equal(state.turns[0].artifacts[0].path,'answer.txt','successful generated files become artifacts');
  assert.equal(state.turns[0].tools[0].detail, 'answer.txt');
  assert(state.turns[0].processElapsedMs > 0);
  const id = state.id;
  assert((await api("/sessions")).sessions.some(s => s.id === id), "first message makes chat visible");
  await stop();
  await start();
  state = await api("/state");
  assert.equal(state.id, id);
  assert(state.events.some((e) => e.type === "tool/result"));
  mode = "text";
  await api("/send", { text: "还记得刚才的文件吗" });
  await until(async () => !(await api("/state")).busy);
  assert(requests.at(-1).messages.some((m) => m.content === "创建一个文件"));
  mode = "search"; calls = 0;
  await api('/send', {text:'  Research   this question  '});
  await until(async () => !(await api('/state')).busy);
  assert.equal(webRequests.length, 2, 'upstream deduplicates parallel search queries');
  assert(webRequests.every(r => r.body.need_content === false && r.body.count === 8 && r.body.ori_question === 'Research this question'));
  assert(requests.at(-1).messages.some(m => m.role === 'tool' && m.content.includes('https://example.com/source')));
  mode = 'fetch'; calls = 0;
  await api('/send', {text:'Read the source'});
  await until(async () => !(await api('/state')).busy);
  assert.equal(webRequests.at(-1).endpoint, 'https://api.deepseek.com/v1/tools/fetch');
  assert(requests.at(-1).messages.some(m => m.role === 'tool' && m.content.includes('Verified page body')));
  invalidWeb = true; mode = 'fetch'; calls = 0;
  await api('/send', {text:'Malformed provider response test'});
  await until(async () => !(await api('/state')).busy);
  assert(requests.at(-1).messages.some(m => m.role === 'tool' && m.content.includes('invalid response body')), 'invalid API response becomes a tool error');
  invalidWeb = false;
  webEnabled = false; mode = 'text';
  await api('/send', {text:'Offline file task'});
  await until(async () => !(await api('/state')).busy);
  assert(!requests.at(-1).tools.some(t => ['web_search','web_fetch'].includes(t.function.name)), 'disabled tools removed');
  webEnabled = true;
  await api('/send', {text:'Tools available again'});
  await until(async () => !(await api('/state')).busy);
  assert(requests.at(-1).tools.some(t => t.function.name === 'web_search'), 'tools re-register');
  mode = "text";
  const outboundUnicode = '你的知识库截止到什么时间？中文👋😀 café 𠮷';
  await fragmentedSend(outboundUnicode);
  await until(async () => !(await api('/state')).busy);
  assert(requests.at(-1).messages.some(m => m.role === 'user' && m.content === outboundUnicode), 'byte-split outgoing Unicode remains exact');
  mode = "unicode";
  await api('/send', {text:'UTF-8 stream split test'});
  await until(async () => !(await api('/state')).busy);
  const unicodeState = await api('/state');
  assert(JSON.stringify(unicodeState).includes('中文跨块测试：你好，世界！👋😀 café 𠮷'));
  assert(!JSON.stringify(unicodeState).includes('\uFFFD'));
  assert.equal(unicodeState.turns.at(-1).processElapsedMs, 0, 'plain text has no made-up process');
  mode = 'streaming';
  await api('/send', {text:'流式思考测试'});
  let partial = await until(async () => {
    const s = await api('/state'); const t = s.turns.at(-1);
    return t.reasoning === '先核对' && t.busy ? t : null;
  });
  assert.equal(partial.text, ''); assert(partial.processActive);
  partial = await until(async () => {
    const s = await api('/state'); const t = s.turns.at(-1);
    return t.text === '你好' && t.busy ? t : null;
  });
  assert(!partial.processActive, 'process settles when final text begins');
  assert(partial.processElapsedMs >= 300);
  await until(async () => !(await api('/state')).busy);
  const completed = (await api('/state')).turns.at(-1);
  assert.equal(completed.text, '你好，世界！');
  assert.equal(completed.reasoning, '先核对事实。');
  assert(!completed.processActive);
  await stop(); await start();
  const resumed = (await api('/state')).turns.at(-1);
  assert.deepEqual(resumed, completed, 'process text and duration persist exactly');
  for (const interruptedMode of ['hang-reasoning', 'fail-stream']) {
    mode = interruptedMode;
    await api('/send', {text:'中断过程保留测试 ' + mode});
    await until(async () => (await api('/state')).turns.at(-1).text === '未完成的正文');
    if (mode === 'hang-reasoning') await api('/cancel');
    await until(async () => !(await api('/state')).busy);
    const interrupted = (await api('/state')).turns.at(-1);
    assert.equal(interrupted.text, '未完成的正文');
    assert.equal(interrupted.reasoning, '已收到的真实思考');
    assert(interrupted.processElapsedMs >= 100);
    assert(!interrupted.processActive);
    await stop(); await start();
    assert.deepEqual((await api('/state')).turns.at(-1), interrupted, 'cancel/error preserves partial content after restart');
  }
  mode = "hang";
  await api("/send", { text: "取消测试" });
  await until(async () => (await api("/state")).live === "partial");
  await api("/cancel");
  await until(async () => !(await api("/state")).busy);
  await stop();
  await start();
  state = await api("/state");
  assert(!state.busy);
  assert.equal(state.id, id);
  // Fork through the user message and through its completed answer. Both
  // paths use upstream fork closers, preserve the source, and remain resumable.
  const sourceBeforeFork = fs.readFileSync(path.join(root, 'sessions', id + '.json'), 'utf8');
  const firstTurn = state.turns[0];
  const forkIds = [];
  for (const includeAssistant of [false, true]) {
    await api('/select', {id});
    const fork = await api('/session/fork', {turnId:firstTurn.id, includeAssistant});
    forkIds.push(fork.id);
    assert.notEqual(fork.id, id);
    const forkState = await api('/state');
    assert.equal(forkState.id, fork.id);
    assert.equal(forkState.isBranch, true);
    const listedFork = (await api('/sessions')).sessions.find(s => s.id === fork.id);
    assert(listedFork.isBranch && listedFork.title.endsWith(' · 分支'));
    assert.equal(forkState.turns.length, 1);
    assert.equal(forkState.turns[0].userText, '创建一个文件');
    assert.equal(forkState.turns[0].tools.length, includeAssistant ? 1 : 0);
    assert.equal(forkState.turns[0].text, includeAssistant ? firstTurn.text : '');
    const forkDisk = JSON.parse(fs.readFileSync(path.join(root, 'sessions', fork.id + '.json'), 'utf8'));
    assert.equal(forkDisk.header.parentSession, id);
    assert(forkDisk.header.isSeeded);
    assert(forkDisk.events.every((e, i) => e.seq === i));
    assert.equal(forkDisk.events.filter(e => e.type === 'turn/start').length,
      forkDisk.events.filter(e => e.type === 'turn/end').length, 'no dangling fork turn');
    mode = 'text';
    await api('/send', {text:'在分支中继续'});
    await until(async () => !(await api('/state')).busy);
    assert(requests.at(-1).messages.some(m => m.content === '创建一个文件'));
    await stop(); await start();
    assert.equal((await api('/state')).id, fork.id);
    assert.equal((await api('/state')).turns.at(-1).userText, '在分支中继续');
    assert.equal(fs.readFileSync(path.join(root, 'sessions', id + '.json'), 'utf8'), sourceBeforeFork);
  }
  for (const forkId of forkIds) await api('/session/delete', {id:forkId});
  await api('/select', {id});
  await api('/session/archive', {id, archived:true});
  assert(!(await api('/sessions')).sessions.some(s => s.id === id));
  assert((await api('/sessions', {archived:true})).sessions.some(s => s.id === id));
  assert.notEqual((await api('/state')).id, id, 'archiving active session selects a new session');
  assert.equal((await api('/sessions')).sessions.length, 0, 'replacement empty chat hidden');
  await stop(); await start();
  assert((await api('/sessions', {archived:true})).sessions.some(s => s.id === id), 'archive survives restart');
  await api('/session/archive', {id, archived:false});
  assert((await api('/sessions')).sessions.some(s => s.id === id));
  await api('/select', {id});
  assert((await api('/state')).events.some(e => e.type === 'user/message'));
  await api('/session/delete', {id});
  assert.notEqual((await api('/state')).id, id);
  assert(!fs.existsSync(path.join(root, 'sessions', id + '.json')), 'delete removes stored transcript');
  await stop(); await start();
  assert(!(await api('/sessions')).sessions.some(s => s.id === id), 'delete survives restart');
  const endpoint = fs.readFileSync(path.join(root, 'runtime-port'), 'utf8');
  const gone = await fetch('http://127.0.0.1:' + endpoint + '/select', {method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify({id})});
  assert.equal(gone.status,400, 'deleted IDs cannot be silently recreated');
  mode = 'unicode';
  for (const effort of [undefined, 'none', 'low', 'high', 'max']) {
    reasoningEffort = effort;
    await api('/send', {text:'检查思考强度'});
    await until(async () => !(await api('/state')).busy);
    const outbound = requests.at(-1);
    assert.equal(outbound.thinking.type, effort === 'none' ? 'disabled' : 'enabled');
    assert.equal(outbound.reasoning_effort, effort === 'none' ? undefined : effort || 'high');
  }
  assert(requests.at(-1).messages.some(m => m.role === 'system' && m.content.includes('SMIL') && m.content.includes('SVG')), 'animation instruction reaches model');
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
  fs.writeFileSync(path.join(workspace,'图片.png'), png);
  fs.writeFileSync(path.join(workspace,'备注.txt'), '中文附件');
  const attachments = [{path:'图片.png',name:'图片.png',mime:'image/png'}, {path:'备注.txt',name:'备注.txt',mime:'text/plain'}];
  await api('/send', {text:'看看图片', attachments});
  await until(async () => !(await api('/state')).busy);
  const attachmentState = await api('/state');
  assert.equal(attachmentState.turns.at(-1).userText, '看看图片');
  assert.equal(attachmentState.turns.at(-1).attachments.length, 2);
  assert(!JSON.stringify(attachmentState).includes(png.toString('base64')), 'state never contains image base64');
  let imageMessage = requests.at(-1).messages.findLast(m => m.role === 'user');
  assert(imageMessage.content.some(b => b.type === 'image_url' && b.image_url.url === 'data:image/png;base64,'+png.toString('base64')));
  assert(imageMessage.content.some(b => b.type === 'text' && b.text.includes('备注.txt')));
  fs.writeFileSync(path.join(workspace,'图片.png'), 'changed working file');
  await stop(); await start();
  assert.equal((await api('/state')).turns.at(-1).attachments[0].name, '图片.png');
  await api('/send', {text:'继续'});
  await until(async () => !(await api('/state')).busy);
  assert(requests.at(-1).messages.some(m => Array.isArray(m.content) && m.content.some(b => b.type === 'image_url' && b.image_url.url.endsWith(png.toString('base64')))), 'history replays immutable image after restart');
  fs.writeFileSync(path.join(workspace,'图片.png'), png);
  await api('/send', {text:'',attachments:[attachments[0]]});
  await until(async () => !(await api('/state')).busy);
  assert.equal((await api('/state')).turns.at(-1).attachments.length,1,'image-only messages accepted');
  const attachmentPort = fs.readFileSync(path.join(root,'runtime-port'),'utf8');
  modelName = 'deepseek-v4-pro';
  const unsupported = await fetch('http://127.0.0.1:'+attachmentPort+'/send', {method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify({text:'look',attachments:[attachments[0]]})});
  assert.equal(unsupported.status,400,'known text-only models reject images explicitly');
  const requestsBeforeModelSwitch = requests.length;
  await api('/send', {text:'切换模型后继续分析之前图片'});
  await until(async () => !(await api('/state')).busy);
  assert.equal(requests.length,requestsBeforeModelSwitch+1,'text-only continuation still requests model');
  assert.equal(requests.at(-1).model,'deepseek-v4-pro','model change takes effect');
  assert(!requests.at(-1).messages.some(m => Array.isArray(m.content) && m.content.some(b => b.type === 'image_url')),'Pro receives no historical images');
  assert((await api('/state')).turns.some(t => t.attachments?.length),'history keeps image metadata');
  modelName = 'deepseek-flash';
  await api('/send',{text:'切回 Flash'});
  await until(async () => !(await api('/state')).busy);
  assert(requests.at(-1).messages.some(m => Array.isArray(m.content) && m.content.some(b => b.type === 'image_url')),'Flash receives historical images again');

  modelName = 'test-model';

  for (const bad of [[{path:'../runtime/runtime-port',mime:'image/png'}], [{path:'备注.txt',mime:'image/png'}], Array(9).fill(attachments[0])]) {
    const response = await fetch('http://127.0.0.1:'+attachmentPort+'/send', {method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify({text:'invalid',attachments:bad})});
    assert.equal(response.status,400,'invalid attachment is rejected before sending');
  }
  const p = fs.readFileSync(path.join(root, "runtime-port"), "utf8");
  assert.equal((await fetch("http://127.0.0.1:" + p + "/state")).status, 401);
  mode='length';
  await api('/send',{text:'输出被服务端截断'});
  await until(async()=>!(await api('/state')).busy);
  const limited=await api('/state');
  assert(JSON.parse(limited.lastError).kind==='max-tokens');
  assert(limited.turns.at(-1).reasoning.includes('实际思考片段'));
  assert(!('max_tokens' in requests.at(-1)),'client imposes no output ceiling');

  // A partial tool JSON must never execute when the provider ends at its output ceiling.
  mode='length-tool';const executedBefore=toolCalls.length;
  await api('/send',{text:'截断的工具参数'});await until(async()=>!(await api('/state')).busy);
  assert.equal(toolCalls.length,executedBefore);
  assert.equal(JSON.parse((await api('/state')).lastError).kind,'max-tokens');
  // Exercise the actual bundled desktop policy, transport, durable replay and UI projection.
  const settled = () => until(async()=>{const s=await api('/state');return !s.busy?s:null;});
  const eventsOnDisk = id => JSON.parse(fs.readFileSync(path.join(root,'sessions',id+'.json'),'utf8')).events;
  modelName='deepseek-flash'; mode='compact-ok';
  await api('/select');
  await api('/send',{text:'需要保留的原始任务。'.repeat(3000)}); await settled();
  overflowBudget=1; const beforeSummary=summaryCalls;
  await api('/send',{text:'继续完成原始任务'});
  const recovered=await settled();
  assert.equal(recovered.lastError,'','context overflow automatically recovered');
  assert.equal(summaryCalls,beforeSummary+1);
  assert(eventsOnDisk(recovered.id).some(e=>e.type==='compaction/summary'));
  assert(requests.at(-1).messages.some(m=>String(m.content).includes('<compacted-summary>')));
  assert.equal(recovered.turns.length,2,'summary is not a fake user message');
  assert.equal(recovered.turns[0].userText,'需要保留的原始任务。'.repeat(3000),'visible history stays intact');
  await stop(); await start();
  await api('/send',{text:'重新启动后继续'});await settled();
  assert(requests.at(-1).messages.some(m=>String(m.content).includes('<compacted-summary>')),'compacted request survives restart');
  assert.equal((await api('/state')).turns.length,3);

  await api('/select');mode='compact-ok';
  for(let n=0;n<10;n++){if(n===9)mode='compact-pressure';await api('/send',{text:'pressure history '.repeat(5800)});await settled();}
  mode='compact-ok';const pressureBefore=summaryCalls;
  await api('/send',{text:'下一轮自动检查上下文'});const pressured=await settled();
  assert.equal(pressured.lastError,'');
  assert(summaryCalls>pressureBefore,'pressure compacts proactively without an overflow response');

  await api('/select');mode='compact-ok';
  await api('/send',{text:'压缩失败时仍保留原文。'.repeat(3000)});await settled();
  summaryMode='fail';overflowBudget=1;
  await api('/send',{text:'压缩服务失败'});const failedCompact=await settled();
  assert(failedCompact.lastError.includes('CONTEXT_WINDOW_EXCEEDED'));
  assert(!eventsOnDisk(failedCompact.id).some(e=>e.type==='compaction/summary'));
  summaryMode='ok';
  await api('/send',{text:'恢复服务后继续'});await settled();
  assert(requests.at(-1).messages.some(m=>String(m.content).includes('压缩失败时仍保留原文')));

  await api('/select');await api('/send',{text:'取消压缩测试。'.repeat(3000)});await settled();
  summaryMode='hang';overflowBudget=1;const cancelBefore=summaryCalls;
  await api('/send',{text:'开始压缩'});await until(()=>summaryCalls>cancelBefore);
  await api('/cancel');const canceledCompact=await settled();
  assert(!eventsOnDisk(canceledCompact.id).some(e=>e.type==='compaction/summary'));
  summaryMode='ok';await api('/send',{text:'取消后继续'});const continued=await settled();
  assert.equal(continued.lastError,'');

  await api('/select');mode='tool';calls=0;largeTool=true;
  await api('/send',{text:'先取得较长的工具结果'});await settled();largeTool=false;
  mode='compact-ok';overflowBudget=1;
  await api('/send',{text:'继续处理结果'});const pruned=await settled();
  assert.equal(pruned.lastError,'');
  assert(eventsOnDisk(pruned.id).some(e=>e.type==='compaction/prune'),'desktop tool-result pruning is connected');
  assert(eventsOnDisk(pruned.id).some(e=>e.type==='tool/result'&&JSON.stringify(e).includes('工具原始结果。'.repeat(5000))),'durable original tool output is retained');

  await api('/select');mode='compact-ok';
  await api('/send',{text:'持续超限时不无限重试。'.repeat(3000)});await settled();
  overflowBudget=5;const retryStart=requests.length;
  await api('/send',{text:'无法恢复的上下文超限'});const exhausted=await settled();
  assert(exhausted.lastError.includes('CONTEXT_WINDOW_EXCEEDED'));
  assert.equal(requests.length-retryStart,3,'one rejected request, one summary, one retry only');
  overflowBudget=0;

  mode='compact-ok';titleMode='ok';await api('/select');const titlesBefore=titleRequests.length;
  await api('/send',{text:'请帮我整理这些文档并分析里面的内容'});await settled();
  const titled=await until(async()=>{const s=await api('/state');return s.title==='文件整理与内容分析'?s:null;});
  assert.equal(titleRequests.length,titlesBefore+1);
  assert.equal(titleRequests.at(-1).thinking.type,'disabled');
  assert(!titleRequests.at(-1).tools,'title generation does not execute tools');
  assert.equal((await api('/sessions')).sessions.find(s=>s.id===titled.id).title,titled.title);
  await api('/send',{text:'继续'});await settled();assert.equal(titleRequests.length,titlesBefore+1);
  await stop();await start();assert.equal((await api('/state')).title,'文件整理与内容分析');
  titleMode='fail';await api('/select');await api('/send',{text:'标题失败保留这个临时标题'});await settled();
  await until(()=>titleRequests.length===titlesBefore+2);
  assert.equal((await api('/state')).lastError,'');
  assert((await api('/sessions')).sessions.some(s=>s.title==='标题失败保留这个临时标题'));
  titleMode='hold';await api('/select');await api('/send',{text:'删除时不能复活这个对话'});const deleteTitle=await settled();
  await api('/session/delete',{id:deleteTitle.id});await new Promise(r=>setTimeout(r,500));
  assert(!fs.existsSync(path.join(root,'sessions',deleteTitle.id+'.json')));

  console.log(
    "PASS: upstream loop, tool round trip, transcript mapping, durable resume, cancellation, authorization, upstream multi-query search/fetch, malformed response, tool enablement, image transport and durable attachment replay; desktop compaction pressure, overflow recovery, restart, failure, cancellation, pruning and bounded recovery",
  );
} finally {
  await stop();
  bridge.closeAllConnections();
  await new Promise((r) => bridge.close(r));
  fs.rmSync(testHome, { recursive: true, force: true });
}
