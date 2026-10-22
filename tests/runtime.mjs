import http from "node:http";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import assert from "node:assert/strict";
const root = fs.mkdtempSync(path.join(os.tmpdir(), "dsh-android-test-"));
const token = "test-token";
let webEnabled = true, webRequests = [], invalidWeb = false;
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
        model: "test-model",
        hasKey: true,
        maxSteps: 32,
        thinking: false,
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
    res.end(JSON.stringify({ path: "answer.txt", bytes: 5 }));
    return;
  }
  if (req.url === "/web-api") {
    webRequests.push(b);
    res.end(JSON.stringify(invalidWeb ? {unexpected:true} : b.endpoint.endsWith('/search') ? {results: [{url:'https://example.com/source',title:'Source',snippet:'Verified snippet',date:null}]} : {content:'# Retrieved page\nVerified page body'}));
    return;
  }
  if (req.url === "/model") {
    requests.push(b);
    res.setHeader("content-type", "text/event-stream");
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
  const p = fs.readFileSync(path.join(root, "runtime-port"), "utf8");
  assert.equal((await fetch("http://127.0.0.1:" + p + "/state")).status, 401);
  console.log(
    "PASS: upstream loop, tool round trip, transcript mapping, durable resume, cancellation, authorization, upstream multi-query search/fetch, malformed response, tool enablement",
  );
} finally {
  await stop();
  bridge.closeAllConnections();
  await new Promise((r) => bridge.close(r));
  fs.rmSync(root, { recursive: true, force: true });
}
