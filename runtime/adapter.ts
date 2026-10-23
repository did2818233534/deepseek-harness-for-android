import { attachmentImage } from "./attachments.ts";
import { StringDecoder } from "node:string_decoder";
import { LlmAdapter, ToolCallId } from "@deepseek-ai/dsh-llm";
/** Translates the upstream canonical transcript to a Chat Completions transport owned by Android. */
export class AndroidAdapter extends LlmAdapter {
  constructor(
    private bridge,
    private settings,
    private workspace: string,
  ) {
    super();
  }
  async resolveModel(provider, model) {
    return { provider, id: model, name: model };
  }
  async *stream(options) {
    const omitImages = /^(deepseek-v4-pro|deepseek-pro|deepseek-reasoner|deepseek-chat)$/i.test(options.model);
    const messages = options.messages.map((m) => {
      const text = m.content
        .filter((b) => b.type === "text")
        .map((b) => b.text)
        .join("\n");
      if (m.role === "tool")
        return { role: "tool", tool_call_id: m.toolCallId, content: text };
      const out: any = {
        role: m.role === "developer" ? "system" : m.role,
        content: !omitImages && m.content.some(b => b.type === "image") ? m.content.flatMap(b => b.type === "text" ? [{type:"text", text:b.text}] : b.type === "image" ? [attachmentImage(this.workspace, b.attachment)] : []) : (text || (omitImages && m.content.some(b => b.type === "image") ? "[此前发送的图片，本轮未附带]" : "")),
      };
      if (m.role === "assistant") {
        const calls = m.content.filter((b) => b.type === "tool-call");
        if (calls.length)
          out.tool_calls = calls.map((b) => ({
            id: b.id,
            type: "function",
            function: { name: b.name, arguments: b.arguments },
          }));
        const reasoning = m.content
          .filter((b) => b.type === "reasoning")
          .map((b) => b.text)
          .join("");
        if (reasoning) out.reasoning_content = reasoning;
      }
      return out;
    });
    const body: any = {
      model: options.model,
      messages,
      stream: true,
      max_tokens: options.maxTokens ?? 8192,
    };
    if (options.tools?.length)
      body.tools = options.tools.map((t) => ({
        type: "function",
        function: t,
      }));
    const configuredEffort = this.settings().reasoningEffort;
    const effort = ["none", "low", "high", "max"].includes(configuredEffort) ? configuredEffort : "high";
    body.thinking = {type: effort === "none" ? "disabled" : "enabled"};
    if (effort !== "none") body.reasoning_effort = effort;
    if (Buffer.byteLength(JSON.stringify(body), "utf8") > 48 * 1024 * 1024) throw Error("当前对话的图片总量过大，请新建对话或减少图片后重试");
    const response = await this.bridge("/model", body, options.signal, true);
    if (!response.body) throw Error("Model returned no stream");
    const decoder = new StringDecoder("utf8");
    let pending = "",
      text = "",
      reasoning = "",
      finish = "",
      usage;
    const calls = new Map();
    let sawDone = false;
    // Indices remain stable even when reasoning, text and tools are interleaved.
    const started = new Set<number>();
    const begin = (index, blockType) => {
      if (started.has(index)) return [];
      started.add(index);
      return [{ type: "block-start", index, blockType }];
    };
    for await (const raw of response.body) {
      pending += decoder.write(Buffer.from(raw));
      let pos;
      while ((pos = pending.indexOf("\n")) >= 0) {
        const line = pending.slice(0, pos).trim();
        pending = pending.slice(pos + 1);
        if (!line.startsWith("data:")) continue;
        const data = line.slice(5).trim();
        if (data === "[DONE]") {
          sawDone = true;
          continue;
        }
        if (!data) continue;
        const chunk = JSON.parse(data);
        if (chunk.error) throw Error(chunk.error.message || "Model error");
        if (chunk.usage) usage = chunk.usage;
        const c = chunk.choices?.[0];
        if (!c) continue;
        const d = c.delta || {};
        if (d.reasoning_content) {
          for (const b of begin(0, "reasoning")) yield b;
          reasoning += d.reasoning_content;
          yield {
            type: "reasoning-delta",
            index: 0,
            text: d.reasoning_content,
          };
        }
        if (d.content) {
          for (const b of begin(1, "text")) yield b;
          text += d.content;
          yield { type: "text-delta", index: 1, text: d.content };
        }
        for (const t of d.tool_calls || []) {
          const index = 2 + t.index;
          for (const b of begin(index, "tool-call")) yield b;
          const current = calls.get(index) || {
            id: "",
            name: "",
            arguments: "",
          };
          if (t.id) current.id = t.id;
          if (t.function?.name) current.name += t.function.name;
          current.arguments += t.function?.arguments || "";
          calls.set(index, current);
          yield {
            type: "tool-call-delta",
            index,
            id: ToolCallId(current.id),
            ...(t.function?.name ? { name: t.function.name } : {}),
            argumentsDelta: t.function?.arguments || "",
          };
        }
        if (c.finish_reason) finish = c.finish_reason;
      }
    }
    options.signal?.throwIfAborted();
    if (!finish)
      throw Error("模型连接提前结束，请重试；未执行不完整的工具调用。");
    if (started.has(0))
      yield {
        type: "block-end",
        index: 0,
        block: { type: "reasoning", text: reasoning },
      };
    if (started.has(1))
      yield { type: "block-end", index: 1, block: { type: "text", text } };
    for (const [index, c] of calls) {
      if (!c.id || !c.name) throw Error("Incomplete tool call");
      JSON.parse(c.arguments);
      yield {
        type: "block-end",
        index,
        block: {
          type: "tool-call",
          id: ToolCallId(c.id),
          name: c.name,
          arguments: c.arguments,
        },
      };
    }
    if (usage)
      yield {
        type: "usage",
        usage: {
          inputTokens: usage.prompt_tokens || 0,
          outputTokens: usage.completion_tokens || 0,
        },
      };
    yield {
      type: "finish",
      reason: {
        kind:
          finish === "length"
            ? "max-tokens"
            : calls.size
              ? "tool-calls"
              : "stop",
      },
    };
  }
}
