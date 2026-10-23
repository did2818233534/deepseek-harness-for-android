import fs from "node:fs";
import path from "node:path";
import {
  SessionPersistence,
  SessionPersistenceNotFoundError,
  materializeCreateHeader,
  materializeAppendBatch,
  assertContiguous,
} from "@deepseek-ai/dsh-session-persistence";
/** Single-process, single-writer session store. Atomic replacements keep the previous complete snapshot on crashes. */
export class AndroidSessions extends SessionPersistence {
  static root = "";
  owners = new Set<string>();
  summaries = new Map<string, any>();
  remember(id, data) {
    const first=data.events.find(e=>e.type==="user/message");
    const summary={header:data.header,eventCount:data.events.length,revision:String(data.events.length),title:data.title,titleAttempted:data.titleAttempted,archived:data.archived===true,hasMessages:!!first,fallbackTitle:first?.data.source?.userText?.slice(0,45)||first?.data.source?.attachments?.[0]?.name||first?.data.content?.find(b=>b.type==="text")?.text?.slice(0,45)||"新对话"};
    this.summaries.set(id,summary);return summary;
  }
  summary(id) {return this.summaries.get(id)||this.remember(id,this.load(id));}
  failure: Error | null = null;
  constructor(ctx) {
    super(ctx);
    fs.mkdirSync(AndroidSessions.root, { recursive: true });
    ctx.on("session/event", (session, event) => {
      if (!this.owners.has(session.id)) return;
      try {
        this.appendNow(session.id, [event]);
      } catch (e) {
        this.failure = e;
        ctx.agents
          .get(session.id)
          ?.cancel({ kind: "hook", reason: "会话保存失败" });
      }
    });
    ctx.on("session/flush", () => this.flush());
  }
  appendNow(id, events) {
    if (this.failure) throw this.failure;
    const d = this.load(id);
    const batch = materializeAppendBatch(events);
    assertContiguous(id, batch, d.events.length);
    d.events.push(...batch);
    this.save(id, d);
  }
  file(id) {
    if (!/^[a-zA-Z0-9_-]{1,100}$/.test(id)) throw Error("Invalid session id");
    return path.join(AndroidSessions.root, id + ".json");
  }
  load(id) {
    try {
      const data=JSON.parse(fs.readFileSync(this.file(id), "utf8"));this.remember(id,data);return data;
    } catch (e) {
      if (e.code === "ENOENT") throw new SessionPersistenceNotFoundError(id);
      throw e;
    }
  }
  save(id, data) {
    const p = this.file(id);
    const fd = fs.openSync(p + ".tmp", "w", 0o600);
    try {
      fs.writeFileSync(fd, JSON.stringify(data));
      fs.fsyncSync(fd);
    } finally {
      fs.closeSync(fd);
    }
    fs.renameSync(p + ".tmp", p);
    this.remember(id,data);
    const dir = fs.openSync(AndroidSessions.root, "r");
    try {
      fs.fsyncSync(dir);
    } finally {
      fs.closeSync(dir);
    }
  }
  async create(header, options) {
    options?.signal?.throwIfAborted();
    if (fs.existsSync(this.file(header.id)))
      throw Error("Session already exists");
    this.save(header.id, {
      header: materializeCreateHeader(header),
      events: [],
      inheritedEventCount: options?.inheritedEventCount ?? 0,
    });
    return this.open(header.id, "write");
  }
  async open(id, access, options) {
    options?.signal?.throwIfAborted();
    const initial = this.load(id);
    if (access === "write" && this.owners.has(id))
      throw Error("Session already owned");
    if (access === "write") this.owners.add(id);
    let closed = false;
    const check = (write = false) => {
      if (closed) throw Error("Session handle closed");
      if (write && access !== "write") throw Error("Session is read only");
    };
    const close = async () => {
      if (closed) return;
      closed = true;
      if (access === "write") this.owners.delete(id);
    };
    return {
      id,
      header: initial.header,
      inheritedEventCount: initial.inheritedEventCount,
      access,
      read: async (offset = 0, length?, opts?) => {
        check();
        opts?.signal?.throwIfAborted();
        const d = this.load(id);
        return {
          events: d.events.slice(
            offset,
            length === undefined ? undefined : offset + length,
          ),
          eventState: "detached",
        };
      },
      append: async (events, opts?) => {
        check(true);
        opts?.signal?.throwIfAborted();
        this.appendNow(id, events);
      },
      flush: async () => check(true),
      close,
      [Symbol.asyncDispose]: close,
    };
  }
  setTitle(id, title?: string) {
    const data = this.load(id);
    data.titleAttempted = true;
    if (title) data.title = title;
    this.save(id, data);
  }
  setArchived(id, archived: boolean) {
    const data = this.load(id);
    data.archived = archived;
    this.save(id, data);
  }
  remove(id) {
    this.load(id); // Validate existence before mutating ownership.
    fs.unlinkSync(this.file(id));
    this.owners.delete(id);this.summaries.delete(id);
    const dir = fs.openSync(AndroidSessions.root, "r");
    try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
  }
  async flush() {
    if (this.failure) throw this.failure;
  }
  async stat(id) {
    try {
      const d=this.summary(id);return {header:d.header,eventCount:d.eventCount,revision:d.revision};
    } catch (e) {
      if (e instanceof SessionPersistenceNotFoundError) return undefined;
      throw e;
    }
  }
  async list() {
    return Promise.all(
      fs
        .readdirSync(AndroidSessions.root)
        .filter((n) => n.endsWith(".json"))
        .map((n) => this.stat(n.slice(0, -5))),
    );
  }
}
