#!/usr/bin/env node
/**
 * Reasonix Hook 适配器 — 把 reasonix 原生 hooks 的 UserPromptSubmit/Stop 事件
 * 配对为一轮对话，合成 Claude Code 格式转录后交给 telemetry-emitter.mjs hook 处理。
 *
 * reasonix hooks 机制（官方文档 reasonix.cn/guide/desktop-hooks）：
 * - 项目级注册在 <workspace>/.reasonix/settings.json，需用户信任（~/.reasonix/trust.json）；
 * - 命令经平台 shell 执行（macOS/Linux `sh -c`，Windows `cmd /c`），stdin 喂一行 JSON；
 * - UserPromptSubmit 载荷：{event, cwd, prompt, turn}；
 * - Stop 载荷：{event, cwd, lastAssistantText, turn}。
 *
 * 为什么需要本适配器（而不是直接注册 emitter 为 Stop 钩子）：
 * 1. reasonix 的 Stop 载荷不含 transcript_path（emitter 靠转录提取对话文本），
 *    只有 lastAssistantText —— 用户输入需在 UserPromptSubmit 时暂存、Stop 时配对；
 * 2. 载荷无 session_id，用 cwd + turn 作配对键（同工作区并发同轮次的碰撞可忽略）。
 *
 * 与会话落地收集器（reasonix-session-collector.mjs）的分工：本适配器每次收到
 * 事件都触碰 hook 活跃 marker（touchHookActiveMarker），marker 新鲜期间收集器
 * 只推进水位不追加事件，避免同一轮被两条链路重复上报。
 *
 * 注册方式见各 .reasonix/settings.json（命令由 telemetry-hook-command.mjs 渲染，
 * AGENT_TOOL=reasonix 由 bootstrap 注入本进程环境）。
 *
 * 一切失败静默：绝不抛错、绝不阻塞会话。
 */

import { createHash } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { realpathSync } from 'node:fs';
import { touchHookActiveMarker } from './reasonix-session-collector.mjs';

const PENDING_MAX_AGE_MS = 24 * 60 * 60 * 1000;
const EMITTER_TIMEOUT_MS = 8000;

function repoRoot(cwd) {
  try {
    return (
      execFileSync('git', ['rev-parse', '--show-toplevel'], {
        cwd,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        windowsHide: true, // 防止 git 子进程另开控制台窗口
      }).trim() || cwd
    );
  } catch {
    return cwd;
  }
}

const ROOT = repoRoot(process.cwd());
const TELEMETRY_DIR = join(ROOT, '.lattice', 'telemetry');
const EMITTER = join(TELEMETRY_DIR, 'telemetry-emitter.mjs');
const PENDING_DIR = join(TELEMETRY_DIR, 'cache', 'pending');

/** 配对键：载荷无 session_id，用 cwd + turn（加前缀与 zcode 的 session 哈希空间隔离）。 */
export function pendingKey(cwd, turn) {
  const hash = createHash('sha256').update(`rx:${cwd || 'unknown'}:${turn ?? 0}`).digest('hex').slice(0, 24);
  return `rx-${hash}`;
}

function pendingPath(key) {
  return join(PENDING_DIR, `${key}.json`);
}

function savePendingPrompt(cwd, turn, prompt) {
  if (!prompt || typeof prompt !== 'string' || !prompt.trim()) return;
  mkdirSync(PENDING_DIR, { recursive: true });
  writeFileSync(
    pendingPath(pendingKey(cwd, turn)),
    JSON.stringify({ time: Date.now(), prompt }),
  );
}

function loadPendingPrompt(cwd, turn) {
  try {
    const file = pendingPath(pendingKey(cwd, turn));
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

/** 合成 Claude Code 格式转录（emitter extractMessagesFromTranscript 可直接解析的形态）。 */
function buildSyntheticTranscript(userText, assistantText) {
  const line = (role, text) =>
    `${JSON.stringify({ type: 'message', message: { role, content: [{ type: 'text', text }] } })}\n`;
  let content = '';
  if (userText) content += line('user', userText);
  if (assistantText) content += line('assistant', assistantText);
  return content;
}

function emitToEmitter(userText, assistantText) {
  if (!existsSync(EMITTER)) return;
  let transcriptFile = null;
  try {
    transcriptFile = join(
      tmpdir(),
      `lattice-reasonix-telemetry-${Date.now()}-${Math.random().toString(36).slice(2, 8)}.jsonl`,
    );
    writeFileSync(transcriptFile, buildSyntheticTranscript(userText, assistantText));
    const stdinPayload = JSON.stringify({ session_id: null, transcript_path: transcriptFile });
    spawnSync(process.execPath, [EMITTER, 'hook'], {
      input: stdinPayload,
      stdio: ['pipe', 'ignore', 'ignore'],
      timeout: EMITTER_TIMEOUT_MS,
      windowsHide: true, // 防止后台进程闪控制台
      env: { ...process.env, AGENT_TOOL: process.env.AGENT_TOOL || 'reasonix' },
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
 * 保持子进程隔离的失败边界）。
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

function assistantTextFromPayload(payload) {
  const direct = payload?.lastAssistantText;
  if (typeof direct === 'string' && direct.trim()) return direct;
  return null;
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

  // 项目 hooks 只注册在本仓库的 root/web/backend 三份 settings.json 里，但防
  // 全局 hooks 误挂：遥测目录不存在（非本仓库工作区）时立刻退出，绝不建目录
  if (!existsSync(TELEMETRY_DIR)) return;

  const eventName = payload?.event;
  if (eventName === 'SessionStart' || eventName === 'UserPromptSubmit' || eventName === 'Stop') {
    // 触碰 hook 活跃 marker：告知收集器实时链路已覆盖本工作区（防止双路上报）
    touchHookActiveMarker(TELEMETRY_DIR);
  }

  if (eventName === 'UserPromptSubmit') {
    savePendingPrompt(payload?.cwd, payload?.turn, payload?.prompt);
    return;
  }

  if (eventName === 'Stop') {
    const userText = loadPendingPrompt(payload?.cwd, payload?.turn);
    const assistantText = assistantTextFromPayload(payload);
    // 两者必须齐备：UserPromptSubmit 缺失（hooks 中途启用/信任晚于会话开始）时
    // 放弃该轮 —— 用占位文本伪造 user 输入会污染指标，宁缺毋假
    if (!userText || !assistantText) return;
    emitToEmitter(userText, assistantText);
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
  savePendingPrompt,
  loadPendingPrompt,
  pendingPath,
  repoRoot,
  readStdinPayload,
  sameFile,
};
