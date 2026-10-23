/** UI projection of durable events plus the currently uncommitted model stream.
 * No synthetic reasoning and no tool output/file contents are exposed here. */
export function presentTurns(events: any[], liveFrames: any[], busy: boolean) {
  const turns: any[] = [];
  let turn: any;
  let processStart = 0, processTotal = 0;
  const openProcess = (time: number) => {
    if (!processStart) processStart = time;
    turn.processActive = true;
  };
  const closeProcess = (time: number) => {
    if (processStart) processTotal += Math.max(0, time - processStart);
    processStart = 0;
    turn.processActive = false;
    turn.processElapsedMs = processTotal;
  };
  const chunk = (c: any, time: number, append: boolean) => {
    if (c.type === 'reasoning-delta' && c.text) {
      openProcess(time);
      if (append) turn.reasoning += c.text;
    } else if (c.type === 'tool-call-delta') openProcess(time);
    else if (c.type === 'text-delta' && c.text) {
      closeProcess(time);
      if (append) turn.text += c.text;
    }
  };
  const stream = (records: any[], append = false) => {
    for (const r of records || []) {
      if (r.type === 'chunk') chunk(r.chunk, r.time, append);
      else {
        let time = r.time0;
        const values = r.texts || r.args || [];
        for (let i = 0; i < values.length; i++) {
          if (i) time += r.dt[i - 1];
          chunk({ type: r.type === 'reasoning-chunks' ? 'reasoning-delta' : r.type === 'text-chunks' ? 'text-delta' : 'tool-call-delta', text: values[i] }, time, append);
        }
      }
    }
  };
  for (const e of events) {
    if (e.type === 'user/message') {
      if (turn && processStart) closeProcess(e.time);
      processStart = 0; processTotal = 0;
      turn = {id: String(e.seq), attachments: e.data.source?.attachments || [], userText: e.data.source?.userText ?? (e.data.content || []).filter(b => b.type === 'text').map(b => b.text).join('\n'), text: '', reasoning: '', tools: [], busy: false, startedAt: e.time, endedAt: 0, processActive: false, processElapsedMs: 0};
      turns.push(turn);
    }
    if (!turn) continue;
    if (e.type === 'assistant/message') {
      stream(e.data.stream);
      for (const b of e.data.message.content || []) {
        if (b.type === 'text') turn.text += b.text;
        if (b.type === 'reasoning') turn.reasoning += (turn.reasoning ? '\n\n' : '') + b.text;
      }
    } else if (e.type === 'assistant/attempt') {
      // Failed attempts are intentionally absent from model-visible history, but
      // their delivered text is still real UI content and must survive settlement.
      stream(e.data.stream, true);
    } else if (e.type === 'tool/call') {
      openProcess(e.time);
      let args: any = {};
      try { args = JSON.parse(e.data.arguments); } catch {}
      const target = args.path || args.url || (Array.isArray(args.queries) ? args.queries.join('、') : '') || args.query || '';
      turn.tools.push({id: e.data.callId, name: e.data.name, status: 'running', detail: String(target).slice(0, 180)});
    } else if (e.type === 'tool/result') {
      const t = turn.tools.find(t => t.id === e.data.message.toolCallId);
      if (t) { t.status = e.data.error?.name === 'AbortError' || /ABORT|CANCEL/i.test(e.data.error?.code || '') ? 'interrupted' : e.data.message.isError ? 'error' : 'completed'; }
    } else if (e.type === 'turn/end') {
      closeProcess(e.time);
      turn.endedAt = e.time;
      for (const t of turn.tools) if (t.status === 'running') t.status = 'interrupted';
    }
  }
  if (turn) {
    for (const frame of liveFrames) chunk(frame.chunk, frame.time, true);
    turn.busy = busy;
    if (busy && processStart) turn.processElapsedMs = processTotal + Math.max(0, Date.now() - processStart);
    if (!busy && processStart) closeProcess(turn.endedAt || Date.now());
  }
  return turns.slice(-150);
}
