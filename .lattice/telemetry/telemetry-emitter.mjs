#!/usr/bin/env node
/**
 * Telemetry Emitter — non-intrusive Lattice usage stats collection.
 *
 * Design source: .lattice/context/telemetry-collection.md & telemetry-receiver-analyzer-sync.md.
 *
 * Architecture: "collection side = recording machine, server side = analysis engine."
 * This script records raw facts only (user input + AI reply + environment) and
 * uploads them on git push. No session/round/event_type/skill analysis happens here.
 *
 * Entry points (run from any directory within repo):
 *   node telemetry-emitter.mjs hook       Read stdin JSON (Stop hook), extract transcript, append RawEvent
 *   node telemetry-emitter.mjs flush      Batch upload staging + ContextSnapshot to F2 API
 *   node telemetry-emitter.mjs is-enabled Check if telemetry is enabled for this repo
 *   node telemetry-emitter.mjs status     Print current state
 *
 * Every failure is silent: never throws, never logs to user-visible channels, never blocks.
 */

import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import {
  appendFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import { request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------------------
// Dynamic Repository Root
// ---------------------------------------------------------------------------

function getRepoRoot() {
  try {
    const toplevel = execFileSync('git', ['rev-parse', '--show-toplevel'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      windowsHide: true, // 父进程被平台以 CREATE_NO_WINDOW 启动时，git 不得另开控制台窗口
    }).trim();
    if (toplevel) return toplevel;
  } catch {
    // ignore
  }
  return process.cwd();
}

const REPO_ROOT = getRepoRoot();

function resolveTelemetryDir() {
  if (existsSync(join(REPO_ROOT, '.lattice', 'telemetry'))) {
    return join(REPO_ROOT, '.lattice', 'telemetry');
  }
  if (existsSync(join(REPO_ROOT, 'telemetry'))) {
    return join(REPO_ROOT, 'telemetry');
  }
  if (existsSync(join(REPO_ROOT, '.telemetry'))) {
    return join(REPO_ROOT, '.telemetry');
  }
  return join(REPO_ROOT, '.lattice', 'telemetry');
}

const TELEMETRY_DIR = resolveTelemetryDir();
const CONFIG_FILE = join(REPO_ROOT, 'telemetry.config.json');
const LEGACY_ENABLED_FILE = join(REPO_ROOT, 'telemetry.enabled');
const CACHE_DIR = join(TELEMETRY_DIR, 'cache');
const EVENTS_FILE = join(CACHE_DIR, 'events.ndjson');
const MAX_EVENTS = 10000;
const FLUSH_TIMEOUT_MS = 5000;
// Stop 钩子搭载 flush 的节流窗口：窗口内的后续事件不再重复派发后台 flush。
// post-push 钩子若已配置仍会额外触发，两者幂等（flush 原子重命名暂存文件）。
const FLUSH_THROTTLE_MS = 10 * 60 * 1000;
const API_URL_FILE = join(TELEMETRY_DIR, 'api.url');

const CONFIG_FILES = [
  join(REPO_ROOT, 'telemetry.config.json'),
  join(REPO_ROOT, '.lattice', 'telemetry.config.json'),
  join(REPO_ROOT, 'telemetry.config'),
  join(REPO_ROOT, '.lattice', 'telemetry.config'),
  join(REPO_ROOT, 'telemetry.json'),
  join(REPO_ROOT, '.lattice', 'telemetry.json'),
  join(REPO_ROOT, 'telemetry.enabled'),
  join(REPO_ROOT, '.lattice', 'telemetry.enabled'),
];

// ---------------------------------------------------------------------------
// TelemetryConfig — file existence gate + api_url definition
// ---------------------------------------------------------------------------

function parseTelemetryConfigFile(content) {
  if (!content || !content.trim()) {
    // 空文件（如 touch .lattice/telemetry.enabled）默认开启
    return { enabled: true, apiUrl: '' };
  }

  const trimmed = content.trim();
  // 1. 尝试 JSON 解析
  if (trimmed.startsWith('{')) {
    try {
      const data = JSON.parse(trimmed);
      return {
        enabled: typeof data.enabled === 'boolean' ? data.enabled : true,
        apiUrl: (data.api_url || data.apiUrl || data.url || '').trim(),
      };
    } catch {
      // fallback to line parsing
    }
  }

  // 2. 尝试 key-value 或单行 URL 解析
  let enabled = true;
  let apiUrl = '';
  const lines = trimmed.split(/\r?\n/);
  for (const line of lines) {
    const l = line.trim();
    if (!l || l.startsWith('#')) continue;
    if (/^enabled\s*[:=]\s*false$/i.test(l)) {
      enabled = false;
    } else if (/^enabled\s*[:=]\s*true$/i.test(l)) {
      enabled = true;
    } else if (/^(?:api_url|apiUrl|url)\s*[:=]\s*(.+)$/i.test(l)) {
      const m = l.match(/^(?:api_url|apiUrl|url)\s*[:=]\s*(.+)$/i);
      if (m) apiUrl = m[1].trim().replace(/^["']|["']$/g, '');
    } else if (l.startsWith('http://') || l.startsWith('https://')) {
      if (!apiUrl) apiUrl = l;
    }
  }

  return { enabled, apiUrl };
}

function loadTelemetryConfig() {
  const envEnabled = process.env.TELEMETRY_ENABLED;
  const envApiUrl = process.env.TELEMETRY_API_URL;

  let fileFound = false;
  let fileConfig = { enabled: false, apiUrl: '' };

  for (const file of CONFIG_FILES) {
    try {
      if (existsSync(file)) {
        fileFound = true;
        const content = readFileSync(file, 'utf8');
        fileConfig = parseTelemetryConfigFile(content);
        break;
      }
    } catch {
      // silent
    }
  }

  if (!fileFound) {
    // 没有任何配置文件存在，默认未开启
    return {
      enabled: false,
      apiUrl: envApiUrl ? envApiUrl.trim() : '',
    };
  }

  let finalEnabled = fileConfig.enabled;
  if (envEnabled !== undefined) {
    finalEnabled = envEnabled !== 'false' && envEnabled !== '0';
  }

  let finalApiUrl = envApiUrl ? envApiUrl.trim() : fileConfig.apiUrl;
  if (!finalApiUrl) {
    try {
      if (existsSync(API_URL_FILE)) {
        finalApiUrl = readFileSync(API_URL_FILE, 'utf8').trim().split(/\r?\n/)[0] || '';
      }
    } catch {
      // silent
    }
  }

  return {
    enabled: finalEnabled,
    apiUrl: finalApiUrl,
  };
}

function isEnabled() {
  return loadTelemetryConfig().enabled;
}

function resolveApiUrl() {
  return loadTelemetryConfig().apiUrl;
}

// ---------------------------------------------------------------------------
// Git context — user identity, repo remote, current branch. Best-effort.
// ---------------------------------------------------------------------------

function gitCommand(args) {
  try {
    return execFileSync('git', args, {
      cwd: REPO_ROOT,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      windowsHide: true,
    }).trim();
  } catch {
    return '';
  }
}

function gitUserName() {
  return gitCommand(['config', 'user.name']) || 'unknown';
}

function gitRemoteUrl() {
  return gitCommand(['remote', 'get-url', 'origin']);
}

function gitBranchName() {
  return gitCommand(['rev-parse', '--abbrev-ref', 'HEAD']);
}

// ---------------------------------------------------------------------------
// Identity helpers.
// ---------------------------------------------------------------------------

function eventId() {
  return randomUUID();
}

function nowTimestamp() {
  return Date.now();
}

/**
 * Stable, non-reversible source session key. The service only needs correlation,
 * never the platform's raw conversation identifier or local transcript path.
 */
function sourceSessionId(payload, transcriptPath) {
  const raw =
    payload?.session_id ||
    payload?.sessionId ||
    payload?.conversation_id ||
    payload?.conversationId ||
    transcriptPath;
  if (!raw) return null;
  return createHash('sha256').update(String(raw)).digest('hex');
}

// ---------------------------------------------------------------------------
// Staging Store — append-only NDJSON log with capacity protection.
// ---------------------------------------------------------------------------

function ensureCacheDir() {
  mkdirSync(CACHE_DIR, { recursive: true });
}

function appendEvent(event) {
  ensureCacheDir();
  try {
    if (existsSync(EVENTS_FILE)) {
      const count = countLines(EVENTS_FILE);
      if (count >= MAX_EVENTS) {
        const lines = readFileSync(EVENTS_FILE, 'utf8').split('\n').filter(Boolean);
        const remaining = lines.slice(1);
        writeFileSync(EVENTS_FILE, remaining.join('\n') + (remaining.length ? '\n' : ''));
      }
    }
    appendFileSync(EVENTS_FILE, JSON.stringify(event) + '\n');
  } catch {
    // silent
  }
}

function countLines(file) {
  try {
    const content = readFileSync(file, 'utf8');
    return content ? content.split('\n').filter(Boolean).length : 0;
  } catch {
    return 0;
  }
}

function isValidEvent(event) {
  return (
    event &&
    typeof event === 'object' &&
    typeof event.event_id === 'string' &&
    event.event_id.length > 0
  );
}

function parseEventsFromFile(file) {
  if (!existsSync(file)) return [];
  try {
    const content = readFileSync(file, 'utf8');
    // 1. 尝试逐行标准 NDJSON
    const lines = content.split('\n').map((l) => l.trim()).filter(Boolean);
    const ndjsonEvents = [];
    let lineParseFailed = false;
    for (const line of lines) {
      try {
        const obj = JSON.parse(line);
        if (isValidEvent(obj)) ndjsonEvents.push(obj);
      } catch {
        lineParseFailed = true;
      }
    }
    if (ndjsonEvents.length > 0 && !lineParseFailed) return ndjsonEvents;

    // 2. 兜底：流式提取多行 JSON 对象（应对文件被 IDE 格式化为 multiline JSON 的情况）
    const events = [];
    let depth = 0;
    let start = -1;
    let inString = false;
    let escape = false;

    for (let i = 0; i < content.length; i++) {
      const char = content[i];
      if (escape) {
        escape = false;
        continue;
      }
      if (char === '\\') {
        escape = true;
        continue;
      }
      if (char === '"') {
        inString = !inString;
        continue;
      }
      if (inString) continue;

      if (char === '{') {
        if (depth === 0) start = i;
        depth++;
      } else if (char === '}') {
        depth--;
        if (depth === 0 && start !== -1) {
          try {
            const obj = JSON.parse(content.slice(start, i + 1));
            if (isValidEvent(obj)) events.push(obj);
          } catch {
            // 忽略格式错误的单个对象
          }
          start = -1;
        }
      }
    }
    return events.length > 0 ? events : ndjsonEvents;
  } catch {
    return [];
  }
}

function readAllEvents() {
  const events = parseEventsFromFile(EVENTS_FILE);
  return events.sort((a, b) => (a.generate_time || 0) - (b.generate_time || 0));
}

function clearStaging() {
  if (!existsSync(EVENTS_FILE)) return;
  try {
    unlinkSync(EVENTS_FILE);
  } catch {
    // ignore
  }
}

// ---------------------------------------------------------------------------
// Requirements commit — submodule HEAD SHA for server-side snapshot resolution.
// ---------------------------------------------------------------------------

function getRequirementsCommit() {
  try {
    const reqDir = join(REPO_ROOT, '.lattice', 'requirements');
    if (!existsSync(reqDir)) return null;
    return execFileSync('git', ['-C', reqDir, 'rev-parse', 'HEAD'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      windowsHide: true,
    }).trim() || null;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// AI Model detection — best-effort, null on failure.
// ---------------------------------------------------------------------------

function detectModel() {
  // 1. Environment variables (most explicit)
  for (const envVar of [
    'AI_MODEL',
    'AGY_MODEL',
    'ANTIGRAVITY_MODEL',
    'PI_MODEL',
    'OMP_MODEL',
    'ANTHROPIC_MODEL',
    'OPENAI_MODEL',
  ]) {
    const val = process.env[envVar];
    if (val) {
      const parts = val.split('/');
      if (parts.length >= 2) return { provider: parts[0], model: parts.slice(1).join('/') };
      return { provider: null, model: val };
    }
  }

  // 2. omp: JSON settings first
  try {
    const ompJsonPath = join(process.env.HOME || '', '.omp', 'agent', 'settings.json');
    if (existsSync(ompJsonPath)) {
      const raw = readFileSync(ompJsonPath, 'utf8').trim();
      if (raw) {
        const settings = JSON.parse(raw);
        const defaultModel = settings.modelRoles?.default || settings.default;
        if (defaultModel) {
          const parts = defaultModel.split('/');
          if (parts.length >= 2) return { provider: parts[0], model: parts.slice(1).join('/') };
          return { provider: null, model: defaultModel };
        }
      }
    }
  } catch {
    // continue
  }

  // 2b. omp: YAML settings
  try {
    const ompDir = join(process.env.HOME || '', '.omp', 'agent');
    if (existsSync(ompDir)) {
      for (const f of readdirSync(ompDir)) {
        if (!f.endsWith('.yml') && !f.endsWith('.yaml')) continue;
        const raw = readFileSync(join(ompDir, f), 'utf8');
        let inModelRoles = false;
        for (const line of raw.split('\n')) {
          if (/^modelRoles\s*:/.test(line)) {
            inModelRoles = true;
            continue;
          }
          if (inModelRoles && /^\S/.test(line)) {
            inModelRoles = false;
          }
          if (inModelRoles) {
            const m = line.match(/\bdefault\s*:\s*(.+)/);
            if (m) {
              const val = m[1].trim();
              const parts = val.split('/');
              if (parts.length >= 2) return { provider: parts[0], model: parts.slice(1).join('/') };
              if (val) return { provider: null, model: val };
            }
          }
        }
      }
    }
  } catch {
    // continue
  }

  // 3. pi settings
  try {
    const piPath = join(process.env.HOME || '', '.pi', 'agent', 'settings.json');
    if (existsSync(piPath)) {
      const settings = JSON.parse(readFileSync(piPath, 'utf8'));
      const provider = settings.defaultProvider || null;
      const model = settings.defaultModel || null;
      if (model) return { provider, model };
    }
  } catch {
    // continue
  }

  return { provider: null, model: null };
}

// ---------------------------------------------------------------------------
// Agent tool detection — best-effort.
// ---------------------------------------------------------------------------

function detectAgentTool() {
  if (process.env.AGENT_TOOL) return process.env.AGENT_TOOL;
  if (
    process.env.ANTIGRAVITY_AGENT ||
    process.env.ANTIGRAVITY_CONVERSATION_ID ||
    process.env.ANTIGRAVITY_TRAJECTORY_ID ||
    process.env.AGY_AGENT ||
    process.env.AGY ||
    process.env.AI_AGENT === 'antigravity' ||
    (process.env.JETSKI_APP_DATA_DIR && process.env.JETSKI_APP_DATA_DIR.includes('antigravity'))
  ) {
    return 'agy';
  }
  if (process.env.CURSOR_VERSION || process.env.CURSOR_AGENT) return 'cursor';
  if (process.env.PI_AGENT || process.env.PI_DIR || process.env.PI_MODEL) return 'pi';
  // zcode 同时注入 CLAUDE_* 兼容变量，必须先于 claude-code 判断
  if (process.env.ZCODE_APP_CLI_ENTRY || process.env.ZCODE_SESSION_ID || process.env.ZCODE_PROJECT_DIR) {
    return 'zcode';
  }
  if (process.env.CLAUDE_CODE || process.env.CLAUDE_PROJECT_DIR) return 'claude-code';
  if (process.env.CODEX_VERSION) return 'codex';
  return 'omp';
}

// ---------------------------------------------------------------------------
// Transcript text extraction — read JSONL, find last user + assistant text.
// ---------------------------------------------------------------------------

function extractMessagesFromTranscript(transcriptPath) {
  if (!transcriptPath || !existsSync(transcriptPath)) {
    return { userText: null, assistantText: null };
  }
  let lastUserText = null;
  let lastAssistantText = null;
  try {
    const raw = readFileSync(transcriptPath, 'utf8');
    for (const line of raw.split('\n')) {
      if (!line.trim()) continue;
      try {
        const entry = JSON.parse(line);

        // 1. Antigravity / AGY transcript format
        if (entry.type === 'USER_INPUT' && entry.content) {
          lastUserText = typeof entry.content === 'string' ? entry.content : JSON.stringify(entry.content);
          continue;
        }
        if (entry.type === 'PLANNER_RESPONSE' && entry.content) {
          lastAssistantText = typeof entry.content === 'string' ? entry.content : JSON.stringify(entry.content);
          continue;
        }

        // 1b. Codex rollout format:
        // {type:"response_item", payload:{type:"message", role, content:[{type:"input_text"|"output_text", text}]}}
        // developer/system roles carry env context, not user input — skip them.
        if (entry.type === 'response_item' && entry.payload && entry.payload.type === 'message') {
          const msg = entry.payload;
          const role = msg.role;
          if (role === 'user' || role === 'assistant') {
            const content = msg.content;
            let text = null;
            if (typeof content === 'string') {
              text = content;
            } else if (Array.isArray(content)) {
              const textBlocks = content.filter(
                (b) => b.type === 'input_text' || b.type === 'output_text' || b.type === 'text',
              );
              if (textBlocks.length > 0) {
                text = textBlocks.map((b) => b.text || '').join('\n');
              }
            }
            if (text) {
              if (role === 'user') lastUserText = text;
              if (role === 'assistant') lastAssistantText = text;
            }
          }
          continue;
        }

        // 1c. OMP skill invocation: 用户敲 /xxx 后由客户端展开为
        // {type:"custom_message", customType:"skill-prompt", content:"[IMPORTANT: User invoked ...] + SKILL.md 全文", details:{name,args}}
        // 技能名不以 /skill:xxx 形式传给 agent；此处规范化为 `[skill:name] args`，
        // 服务端据此打技能标签，args 中的需求文档路径可直接用于需求关联。
        if (entry.type === 'custom_message' && entry.customType === 'skill-prompt') {
          const name = entry.details?.name || 'unknown';
          const args = typeof entry.details?.args === 'string' ? entry.details.args : '';
          lastUserText = `[skill:${name}] ${args}`.trim();
          continue;
        }
        // 2. Generic message format —— 两种承载方式，message 内部结构同形：
        //    a) type:"message"：omp / pi / zcode / reasonix 适配器合成，及 omp 原生转录
        //    b) type:"user" | "assistant"：Claude Code 原生转录（直连本脚本，无适配器铺路）
        if (entry.type !== 'message' && entry.type !== 'user' && entry.type !== 'assistant') continue;
        const msg = entry.message;
        if (!msg) continue;
        const role = msg.role;
        const content = msg.content;
        let text = null;
        if (typeof content === 'string') {
          text = content;
        } else if (Array.isArray(content)) {
          const textBlocks = content.filter((b) => b.type === 'text');
          if (textBlocks.length > 0) {
            text = textBlocks.map((b) => b.text || '').join('\n');
          }
        }
        if (!text) continue;
        // Pi skill invocation: 用户敲 /xxx 后客户端展开为 <skill name="..."> 包裹
        // （SKILL.md 全文），技能名只在包裹属性里；规范化为 `[skill:name]`。
        if (role === 'user' && typeof text === 'string' && text.startsWith('<skill name="')) {
          const sm = text.match(/^<skill name="([^"]+)"/);
          if (sm) text = `[skill:${sm[1]}]`;
        }
        if (role === 'user') lastUserText = text;
        if (role === 'assistant') lastAssistantText = text;
      } catch {
        // skip unparseable lines
      }
    }
  } catch {
    // silent
  }
  return { userText: lastUserText, assistantText: lastAssistantText };
}

// ---------------------------------------------------------------------------
// Emitter.hook — Stop hook entry point.
// Reads stdin JSON from platform, extracts transcript, appends RawEvent.
// ---------------------------------------------------------------------------

/**
 * 读取平台经 stdin 喂入的 JSON 载荷。omp / pi 扩展适配层（telemetry-hook.mjs）
 * 经 TELEMETRY_PAYLOAD 环境变量投递 —— Bun 系宿主对 spawn 子进程的 stdin end()
 * 不保证送达 EOF，阻塞读 fd 0 会永久挂起，env 传输无此依赖。
 * 其余平台（claude-code / codex / cursor / zcode）仍走 stdin：优先同步读 fd 0
 * （hook 平台总是以管道喂入、写入后关闭，同步返回最快）；macOS GUI 父进程 /
 * Windows 非阻塞管道等场景下 fd 0 可能以 EAGAIN 失败，此时回退到异步流读取
 * （流内部会自行处理 EAGAIN）。两条路径在 Node ≥18 的 macOS / Windows 上均可用。
 */
async function readStdinPayload() {
  const fromEnv = process.env.TELEMETRY_PAYLOAD;
  if (fromEnv) return fromEnv;
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

async function hook() {
  if (!isEnabled()) return;

  const stdinData = await readStdinPayload();
  if (!stdinData) return; // no stdin available

  let payload;
  try {
    payload = JSON.parse(stdinData);
  } catch {
    return; // malformed stdin
  }

  if (payload.stop_hook_active) return;

  // Cursor 的 stop 载荷可能不带 transcript_path，回退读其注入的环境变量
  const transcriptPath = payload.transcript_path || process.env.CURSOR_TRANSCRIPT_PATH || null;
  if (!transcriptPath) return;

  const { userText, assistantText } = extractMessagesFromTranscript(transcriptPath);
  if (!userText && !assistantText) return;

  // 平台 hook 载荷自带 model 时优先（事实来源，优于配置文件探测；
  // codex Stop / cursor stop / omp、pi 扩展注入均携带该字段）
  const { provider, model } = resolveModelInfo(payload);

  // Ensure user_input_text is non-empty to satisfy FieldValidator
  const safeUserInput = userText || '(system/tool_continuation)';
  const safeAssistantText = assistantText || '(no response text)';

  const event = {
    event_id: eventId(),
    user_input_text: safeUserInput,
    ai_response_text: safeAssistantText,
    generate_time: nowTimestamp(),
    user: gitUserName(),
    repo_remote_url: gitRemoteUrl(),
    branch_name: gitBranchName(),
    agent_tool: detectAgentTool(),
    source_session_id: sourceSessionId(payload, transcriptPath),
    ai_provider: provider,
    ai_model: model,
    requirement_id: null,
    feature_name: null,
    trace_id: null,
    trace_source: null,
    report_time: null,
  };

  appendEvent(event);
  scheduleThrottledFlush();
}

/**
 * Stop 钩子搭载的节流后台 flush — 让上报不依赖 `git config core.hooksPath` 激活的
 * post-push 钩子（git hooks 不入版本库，无法随仓库分发）。窗口内只派发一次；
 * 派发为 detached 后台进程，hook 立即返回，flush 失败留待下个窗口或 post-push 重试。
 */
function scheduleThrottledFlush() {
  try {
    if (!resolveApiUrl()) return; // F2 未配置 — 无意义派发，事件保留在暂存
    ensureCacheDir();
    const markerFile = join(CACHE_DIR, 'last-flush-at');
    let lastAttempt = 0;
    try {
      lastAttempt = parseInt(readFileSync(markerFile, 'utf8').trim(), 10) || 0;
    } catch {
      // 无 marker — 首次派发
    }
    const now = Date.now();
    if (now - lastAttempt < FLUSH_THROTTLE_MS) return;
    writeFileSync(markerFile, String(now));
    const child = spawn(process.execPath, [fileURLToPath(import.meta.url), 'flush'], {
      detached: true,
      stdio: 'ignore',
      windowsHide: true, // Windows 下 detached 后台进程不得闪现控制台窗口
    });
    child.on('error', () => {});
    child.unref();
  } catch {
    // silent — flush 搭载绝不影响事件记录
  }
}


// ---------------------------------------------------------------------------
// Model resolution — 平台 hook 载荷自带 model 时优先（事实来源，优于配置探测）。
// ---------------------------------------------------------------------------

function resolveModelInfo(payload) {
  const platformModel = typeof payload?.model === 'string' ? payload.model.trim() : '';
  if (!platformModel) return detectModel();
  if (platformModel.includes('/')) {
    const slash = platformModel.indexOf('/');
    return { provider: platformModel.slice(0, slash), model: platformModel.slice(slash + 1) };
  }
  // 纯 model id：不从本机默认配置借 provider（模型族不属同一事实来源，借错比缺失更糟）
  return { provider: null, model: platformModel };
}

// ---------------------------------------------------------------------------
// Emitter.flush — batch upload staging + ContextSnapshot to F2 API.
// Atomically renames staging file before upload to prevent race condition.
// ---------------------------------------------------------------------------

async function flush() {
  if (!isEnabled()) return;
  if (!resolveApiUrl()) return; // F2 not deployed — silent no-op, events retained.

  // dsh 会话落地收集：上传前先把 ~/.dsh 持久化会话中属于本仓库的新轮次
  // 暂存进来（headless/web/tui 全模式覆盖，见 dsh-session-collector.mjs）。
  // 依赖注入传参 —— collector 反向静态 import 本模块会因顶层 await main()
  // 形成 ESM 循环求值死锁。
  try {
    const { collectDshSessions } = await import('./dsh-session-collector.mjs');
    await collectDshSessions({
      repoRoot: REPO_ROOT,
      telemetryDir: TELEMETRY_DIR,
      appendEvent,
      eventId,
      gitUserName,
      gitRemoteUrl,
      gitBranchName,
      nowTimestamp,
      sourceSessionId,
    });
  } catch {
    // silent — 收集失败不影响既有暂存的上传
  }

  // reasonix 会话落地收集：与 dsh 同构（分仓库归属 + 精确时间戳），见 reasonix-session-collector.mjs。
  // 注意：本段曾在合并冲突解决时被意外丢弃（f25ff1c4a3 引入、62c731795d 丢失），
  // 导致收集器成为死代码 —— 改动 flush 前段时务必保留 dsh/reasonix 两个收集块。
  try {
    const { collectReasonixSessions } = await import('./reasonix-session-collector.mjs');
    await collectReasonixSessions({
      repoRoot: REPO_ROOT,
      telemetryDir: TELEMETRY_DIR,
      appendEvent,
      eventId,
      gitUserName,
      gitRemoteUrl,
      gitBranchName,
      nowTimestamp,
      sourceSessionId,
    });
  } catch {
    // silent — 收集失败不影响既有暂存的上传
  }

  ensureCacheDir();

  // Find all flushing/pending files to process
  const filesToFlush = [];
  try {
    for (const name of readdirSync(CACHE_DIR)) {
      if (name.startsWith('events.ndjson.flushing-')) {
        filesToFlush.push(join(CACHE_DIR, name));
      }
    }
  } catch {
    // ignore
  }

  // Atomically rename current EVENTS_FILE to a new flushing file
  if (existsSync(EVENTS_FILE)) {
    const flushingFile = join(
      CACHE_DIR,
      `events.ndjson.flushing-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    );
    try {
      renameSync(EVENTS_FILE, flushingFile);
      filesToFlush.push(flushingFile);
    } catch {
      // ignore
    }
  }

  if (filesToFlush.length === 0) return;

  const UUID_V4_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const isUploadable = (e) =>
    e &&
    typeof e === 'object' &&
    typeof e.event_id === 'string' &&
    UUID_V4_REGEX.test(e.event_id) &&
    typeof e.user_input_text === 'string' &&
    e.user_input_text.length > 0 &&
    typeof e.ai_response_text === 'string' &&
    e.ai_response_text.length > 0 &&
    typeof e.generate_time === 'number' &&
    e.generate_time > 0 &&
    typeof e.user === 'string' &&
    e.user.length > 0 &&
    typeof e.repo_remote_url === 'string' &&
    e.repo_remote_url.length > 0 &&
    typeof e.branch_name === 'string' &&
    e.branch_name.length > 0;

  let allEvents = [];
  for (const file of filesToFlush) {
    allEvents.push(...parseEventsFromFile(file));
  }

  allEvents = allEvents
    .filter(isUploadable)
    .sort((a, b) => (a.generate_time || 0) - (b.generate_time || 0));
  if (allEvents.length === 0) {
    for (const file of filesToFlush) {
      try {
        unlinkSync(file);
      } catch {
        // ignore
      }
    }
    return;
  }

  const reportTime = nowTimestamp();
  const requirementsCommit = getRequirementsCommit();
  const body = JSON.stringify({
    events: allEvents,
    report_time: reportTime,
    requirements_commit: requirementsCommit,
  });

  const rawApiUrl = resolveApiUrl();
  if (!rawApiUrl) return;

  let apiUrl = rawApiUrl;
  if (!apiUrl.endsWith('/api/telemetry/batch')) {
    apiUrl = apiUrl.replace(/\/+$/, '') + '/api/telemetry/batch';
  }

  let url;
  try {
    url = new URL(apiUrl);
  } catch {
    return; // malformed API_URL
  }

  const requester = url.protocol === 'https:' ? httpsRequest : httpRequest;
  const success = await new Promise((resolve) => {
    const req = requester(
      url.href,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
          'x-git-user': gitUserName(),
        },
        timeout: FLUSH_TIMEOUT_MS,
      },
      (res) => {
        const isOk = res.statusCode >= 200 && res.statusCode < 300;
        res.resume();
        resolve(isOk);
      },
    );
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
    req.write(body);
    req.end();
  });

  if (success) {
    // Delete successfully uploaded flushing files
    for (const file of filesToFlush) {
      try {
        unlinkSync(file);
      } catch {
        // ignore
      }
    }
  } else {
    // On failure, retain flushing files so next flush can retry
  }
}

// ---------------------------------------------------------------------------
// CLI dispatch.
// ---------------------------------------------------------------------------

function printJson(obj) {
  process.stdout.write(JSON.stringify(obj));
}

async function main() {
  try {
    const [command] = process.argv.slice(2);
    switch (command) {
      case 'hook': {
        await hook();
        return;
      }
      case 'flush': {
        await flush();
        return;
      }
      case 'is-enabled': {
        const enabled = isEnabled();
        process.stdout.write(enabled ? 'true\n' : 'false\n');
        process.exitCode = enabled ? 0 : 1;
        return;
      }
      case 'status': {
        const cfg = loadTelemetryConfig();
        const events = cfg.enabled ? readAllEvents() : [];
        printJson({
          enabled: cfg.enabled,
          api_url: cfg.apiUrl || null,
          staging_count: events.length,
          repo_root: REPO_ROOT,
        });
        return;
      }
      default: {
        return;
      }
    }
  } catch {
    // Silent error boundary — never throw, never block.
    return;
  }
}

await main();

// ---------------------------------------------------------------------------
// Exports for testing.
// ---------------------------------------------------------------------------

export {
  isEnabled,
  hook,
  readStdinPayload,
  flush,
  scheduleThrottledFlush,
  parseEventsFromFile,
  gitUserName,
  gitRemoteUrl,
  gitBranchName,
  readAllEvents,
  clearStaging,
  ensureCacheDir,
  detectModel,
  detectAgentTool,
  resolveModelInfo,
  sourceSessionId,
  extractMessagesFromTranscript,
  appendEvent,
  countLines,
  resolveApiUrl,
  loadTelemetryConfig,
  parseTelemetryConfigFile,
  nowTimestamp,
  eventId,
  getRepoRoot,
  REPO_ROOT,
  CONFIG_FILE,
  LEGACY_ENABLED_FILE,
  CONFIG_FILES,
  EVENTS_FILE,
  CACHE_DIR,
  TELEMETRY_DIR,
  MAX_EVENTS,
  FLUSH_TIMEOUT_MS,
  FLUSH_THROTTLE_MS,
  API_URL_FILE,
};
