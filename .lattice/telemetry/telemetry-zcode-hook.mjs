#!/usr/bin/env node
/**
 * ZCode Hook 适配器 — 把 zcode 的 UserPromptSubmit/Stop 事件配对为一轮对话，
 * 合成 Claude Code 格式转录后交给 telemetry-emitter.mjs hook 处理。
 *
 * 为什么需要本适配器（而不是直接注册 emitter 为 Stop 钩子）：
 * 1. zcode Stop 分发时生成的临时 transcript 只含最后一轮 assistant 文本，
 *    且为 {"message":{...}} 无 type 字段的行，emitter 现有解析器不识别；
 * 2. 该转录不含用户输入 — 需在 UserPromptSubmit 时暂存 prompt，Stop 时配对。
 *
 * 注册方式见仓库 .zcode/config.json（hooks.enabled 必须为 true）。
 * 入口命令：AGENT_TOOL=zcode node telemetry-zcode-hook.mjs
 *
 * 一切失败静默：绝不抛错、绝不阻塞会话。
 */

import { createHash } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PENDING_MAX_AGE_MS = 24 * 60 * 60 * 1000;
const EMITTER_TIMEOUT_MS = 8000;

function repoRoot() {
  try {
    return (
      execFileSync('git', ['rev-parse', '--show-toplevel'], {
        cwd: process.cwd(),
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        windowsHide: true, // 防止 git 子进程另开控制台窗口
      }).trim() || process.cwd()
    );
  } catch {
    return process.cwd();
  }
}

const ROOT = repoRoot();
const EMITTER = join(ROOT, '.lattice', 'telemetry', 'telemetry-emitter.mjs');
const PENDING_DIR = join(ROOT, '.lattice', 'telemetry', 'cache', 'pending');

function pendingPath(sessionId) {
  const key = createHash('sha256').update(String(sessionId || 'unknown')).digest('hex').slice(0, 24);
  return join(PENDING_DIR, `${key}.json`);
}

function savePendingPrompt(sessionId, prompt) {
  if (!prompt || typeof prompt !== 'string' || !prompt.trim()) return;
  mkdirSync(PENDING_DIR, { recursive: true });
  writeFileSync(
    pendingPath(sessionId),
    JSON.stringify({ time: Date.now(), prompt }),
  );
}

function loadPendingPrompt(sessionId) {
  try {
    const file = pendingPath(sessionId);
    if (!existsSync(file)) return null;
    const data = JSON.parse(readFileSync(file, 'utf8'));
    rmSync(file, { force: true });
    if (!data || typeof data.prompt !== 'string') return null;
    if (Date.now() - (data.time || 0) > PENDING_MAX_AGE_MS) return null;
    return data.prompt;
  } catch {
    return null;
  }
}

function pruneStalePending() {
  try {
    if (!existsSync(PENDING_DIR)) return;
    const cutoff = Date.now() - PENDING_MAX_AGE_MS;
    for (const name of readdirSync(PENDING_DIR)) {
      const file = join(PENDING_DIR, name);
      try {
        if (fsMtimeMs(file) < cutoff) rmSync(file, { force: true });
      } catch {
        // skip single file
      }
    }
  } catch {
    // silent
  }
}

function fsMtimeMs(file) {
  try {
    return statSync(file).mtimeMs;
  } catch {
    return 0;
  }
}

/**
 * 提取本轮 assistant 最终回复：优先 zcode Stop 载荷自带的
 * last_assistant_message；缺失时回退解析其临时 transcript
 * （{"message":{role,content:[{type:"text",text}]}} 行，无 type 字段）。
 */
function assistantTextFromPayload(payload) {
  const direct = payload?.last_assistant_message;
  if (typeof direct === 'string' && direct.trim()) return direct;

  const transcriptPath = payload?.transcript_path;
  if (!transcriptPath || !existsSync(transcriptPath)) return null;
  try {
    let last = null;
    for (const line of readFileSync(transcriptPath, 'utf8').split('\n')) {
      if (!line.trim()) continue;
      try {
        const entry = JSON.parse(line);
        const msg = entry?.message;
        if (!msg || msg.role !== 'assistant') continue;
        const text = textFromContent(msg.content);
        if (text) last = text;
      } catch {
        // skip unparseable line
      }
    }
    return last;
  } catch {
    return null;
  }
}

function textFromContent(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    const blocks = content.filter((b) => b?.type === 'text' && typeof b.text === 'string');
    if (blocks.length > 0) return blocks.map((b) => b.text).join('\n');
  }
  return null;
}

/** 合成 Claude Code 格式转录（emitter extractMessagesFromTranscript 可直接解析的形态）。 */
function buildSyntheticTranscript(userText, assistantText) {
  const line = (role, text) =>
    `${JSON.stringify({ type: 'message', message: { role, content: [{ type: 'text', text }] } })}\n`;
  let content = '';
  if (userText) content += line('user', userText);
  if (assistantText) content += line('assistant', assistantText);
  return content;
}

function emitToEmitter(payload, userText, assistantText) {
  if (!existsSync(EMITTER)) return;
  let transcriptFile = null;
  try {
    transcriptFile = join(tmpdir(), `lattice-zcode-telemetry-${Date.now()}-${Math.random().toString(36).slice(2, 8)}.jsonl`);
    writeFileSync(transcriptFile, buildSyntheticTranscript(userText, assistantText));
    const stdinPayload = JSON.stringify({
      session_id: payload?.session_id ?? null,
      transcript_path: transcriptFile,
      model: typeof payload?.model === 'string' ? payload.model : undefined,
      stop_hook_active: payload?.stop_hook_active === true,
    });
    spawnSync(process.execPath, [EMITTER, 'hook'], {
      input: stdinPayload,
      stdio: ['pipe', 'ignore', 'ignore'],
      timeout: EMITTER_TIMEOUT_MS,
      windowsHide: true,
    });
  } catch {
    // silent
  } finally {
    if (transcriptFile) {
      try {
        rmSync(transcriptFile, { force: true });
      } catch {
        // ignore
      }
    }
  }
}

/**
 * 读取平台经 stdin 喂入的 JSON 载荷。优先同步读 fd 0；非阻塞管道（macOS
 * GUI 父进程 / Windows 部分场景）会以 EAGAIN 失败，回退到异步流读取。
 * 与 telemetry-emitter.mjs 的 readStdinPayload 保持同一实现（本脚本刻意
 * 不 import emitter —— 保持子进程隔离的失败边界）。
 */
async function readStdinPayload() {
  try {
    return readFileSync(0, 'utf8');
  } catch {
    // fall through to async reader
  }
  try {
    let data = '';
    process.stdin.setEncoding('utf8');
    for await (const chunk of process.stdin) data += chunk;
    return data;
  } catch {
    return '';
  }
}

/**
 * 判定两个路径是否指向同一文件。Windows 文件系统大小写不敏感、盘符大小写
 * 不稳定（argv[1] 与 import.meta.url 可能一个是 `C:` 一个是 `c:`），直接
 * 字符串比较会漏判导致钩子静默失效；用 realpath 归一化 + win32 大小写
 * 折叠兜底。
 */
function sameFile(a, b) {
  if (!a || !b) return false;
  try {
    const ra = realpathSync(a);
    const rb = realpathSync(b);
    if (ra === rb) return true;
    return process.platform === 'win32' && ra.toLowerCase() === rb.toLowerCase();
  } catch {
    return false;
  }
}

async function main() {
  const stdinData = await readStdinPayload();
  if (!stdinData) return;

  let payload;
  try {
    payload = JSON.parse(stdinData);
  } catch {
    return;
  }

  const eventName = payload?.hook_event_name;
  if (eventName === 'UserPromptSubmit') {
    savePendingPrompt(payload?.session_id, payload?.prompt);
    return;
  }

  if (eventName === 'Stop') {
    if (payload?.stop_hook_active === true) return;
    const userText = loadPendingPrompt(payload?.session_id);
    const assistantText = assistantTextFromPayload(payload);
    if (!userText && !assistantText) return;
    emitToEmitter(payload, userText, assistantText);
    pruneStalePending();
  }
}

// 仅在作为入口被直接执行时读取 stdin（被测试 import 时不阻塞）
const isEntrypoint =
  Boolean(process.argv[1]) && sameFile(fileURLToPath(import.meta.url), process.argv[1]);

if (isEntrypoint) {
  main().catch(() => {
    // Telemetry must never affect an agent session.
  });
}

// ---------------------------------------------------------------------------
// Exports for testing.
// ---------------------------------------------------------------------------

export {
  buildSyntheticTranscript,
  assistantTextFromPayload,
  textFromContent,
  savePendingPrompt,
  loadPendingPrompt,
  pendingPath,
  repoRoot,
  readStdinPayload,
  sameFile,
};
