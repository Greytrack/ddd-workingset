#!/usr/bin/env node
/**
 * Reasonix 会话落地收集器 — 从 reasonix 的持久化会话文件中提取用户/AI 轮次并暂存为 RawEvent。
 *
 * 四种存储均支持（互为兜底）：
 *
 * 1. 新版 v4（旧版桌面，Windows）：`%APPDATA%\reasonix\projects\<路径改写>\sessions-v4\<hash>\events.frames`
 *    - 文件为 RX4F 帧头 + 多段 zstd 分帧压缩；解压后是**无换行的连续 JSON 记录**（batch/begin | batch/event |
 *      batch/end），需 Node ≥22.15（内置 zstd）。
 *    - batch/event 的 payload 为 **base64** 的 `{message:{role,content,raw_content,createdAt,...}}`。
 *    - **真实用户输入**带 `raw_content` 字段（host 注入的 `<session-context>` / `<workspace>` 无此字段，需排除）。
 *    - 时间戳：assistant 消息无自带时间，取所在 commit（batch/begin）的 `createdAt` 作**提交级精度**；
 *      兜底 user 消息的 `createdAt`（毫秒），再兜底文件 mtime。
 * 2. Reasonix STUDIO 项目会话：`%APPDATA%\reasonix\projects\<路径改写>\sessions\<session>.jsonl`
 *    - 同名 `.ckpt/turn-N.json` 是**权威轮次记录**：`prompt` 是 host 剥离后的真实用户输入，
 *      `msgIndex` 指向 jsonl 中该轮 user 消息，`time` 为轮次开始时间。收集器按它切轮，
 *      从 jsonl 的 `(msgIndex, 下一轮 msgIndex)` 区间取最后一条 assistant 文本。
 *    - 无 ckpt 的旧会话回退到 jsonl：user 消息 = host 注入块（`<workspace>`、`<execution-policy>` 等）
 *      + 真实输入，需剥离注入块后才能上报真实输入。
 *    - 同名 `.jsonl.meta` 含 `model`、`updated_at`、`turns` 与 `in_flight_turn`（最后一条进行中轮次的
 *      `start_message_index`）；同名 `.events.jsonl` 是增量消息日志（`replace`/`append` + `created_at`），
 *      回退路径用于取每轮精确时间。
 *    - 带 `imported_from` 的 `v4-*.jsonl` 是会话迁移副本，已由 v4 通道收集，必须跳过防重复。
 * 3. Reasonix STUDIO 桌面会话：`%APPDATA%\reasonix\desktop-sessions-v5\by-id\<id>\events.frames`
 *    - 与 v4 相同的线性帧格式；项目归属取同目录 `header.json` 的 `cwd`。
 * 4. 旧版明文（legacy 兜底）：`~/.reasonix/sessions/code-<项目>[-<时间戳>].jsonl`（OpenAI chat 格式，任何 Node 可读），
 *    时间戳从同名 `.events.jsonl` 的 model.final 取。
 *
 * 归属（方案 A：分仓库）：v4 / STUDIO 项目会话用 `%APPDATA%\reasonix\desktop-projects.json` 的
 * `projects[].root` 做权威注册表（项目目录名 = root 把 `:` 与 `\` 替换为 `-`，比较忽略大小写）；
 * STUDIO 桌面会话用 `header.json` 的 `cwd`；旧格式用文件名 token 匹配。仅收集 git 根 == 当前仓库的会话，
 * 其余交由各自仓库的 flush。
 *
 * 水位：v4 / 桌面会话按全局 seq，STUDIO 项目会话与旧格式按轮次序号推进；**只推进已完结轮次**
 * （下一 user 边界已确认、meta 无 `in_flight_turn`，或文件已静默超过 {@link SETTLE_MS}），
 * 进行中的轮次下次再收——避免 dsh 那个"水位跳过进行中轮次导致丢轮"的问题。
 *
 * 与实时 hook 链路的分工：reasonix 原生支持 hooks（UserPromptSubmit/Stop，见
 * telemetry-reasonix-hook.mjs 与各 .reasonix/settings.json）。wrapper 每个事件都会触碰
 * hook 活跃 marker；marker 新鲜（24h 窗口）时本收集器**只推进水位、不追加事件**——
 * 活跃期轮次由 hook 链路实时上报，避免同一轮被两路重复记；marker 停更（信任撤销/hooks
 * 失效）后自动接管，且水位已跟到头部，不会把 hook 期轮次再收一遍。
 *
 * 由 telemetry-emitter 的 flush 调用（依赖注入传参，避免 ESM 循环）；一切失败静默，绝不阻塞会话。
 */

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { basename, dirname, join } from 'node:path';
import zlib from 'node:zlib';

/** 首次收集只回看 24h 内有更新的会话，避免首次 flush 上传全部历史 */
const INITIAL_LOOKBACK_MS = 24 * 60 * 60 * 1000;
/** 会话文件需静默超过该时长，才把"最后一轮"视为已完结（防半写轮次被误收） */
const SETTLE_MS = 60 * 1000;
/**
 * reasonix hook 链路活跃 marker 的新鲜窗口：wrapper 每次收到
 * UserPromptSubmit / Stop 都会触碰该 marker；窗口内收集器让位于实时
 * hook 链路（只推进水位、不追加事件），避免同一轮被两路重复上报。
 * marker 停更超过窗口（信任被撤销 / hooks 失效）→ 收集器自动接管。
 */
const HOOK_ACTIVE_WINDOW_MS = 24 * 60 * 60 * 1000;
const ZSTD_MAGIC = Buffer.from([0x28, 0xb5, 0x2f, 0xfd]);
/** v4 记录边界（解压后为无换行的连续 JSON 对象） */
const V4_RECORD_SPLIT = /(?=\{"schemaVersion":4,"codec":"reasonix\.session\.linear\/v4","recordType":)/;

/**
 * 收集属于本仓库的 reasonix 新轮次并追加到暂存（v4 为主 + 旧格式兜底）。
 * @param {object} deps - emitter 注入的依赖（与 dsh 收集器同构）
 * @param {string} deps.repoRoot - 当前仓库根（归属判定基准）
 * @param {string} deps.telemetryDir - 本仓库遥测目录（水位状态文件位置）
 * @param {Function} deps.appendEvent - 暂存追加函数
 * @param {Function} deps.eventId - UUID 生成
 * @param {Function} deps.gitUserName - git user.name
 * @param {Function} deps.gitRemoteUrl - origin 远程地址
 * @param {Function} deps.gitBranchName - 当前分支
 * @param {Function} deps.sourceSessionId - 会话标识哈希（payload, transcriptPath）=> hash
 * @returns {Promise<number>} 追加的事件数（供测试断言）
 */
export async function collectReasonixSessions(deps) {
  const { repoRoot, telemetryDir, appendEvent } = deps ?? {};
  if (!repoRoot || !telemetryDir || typeof appendEvent !== 'function') return 0;

  const state = readState(telemetryDir);
  const initAt = state.initAt || Date.now() - INITIAL_LOOKBACK_MS;
  const ctx = {
    repoRoot,
    appendEvent,
    eventId: deps.eventId,
    sourceSessionId: deps.sourceSessionId,
    // hook 链路活跃期间让位：finalize 仍推进水位，但不追加事件（防双路上报）
    skipAppends: isHookActive(telemetryDir),
    identity: {
      user: deps.gitUserName?.() ?? 'unknown',
      repo_remote_url: deps.gitRemoteUrl?.() ?? '',
      branch_name: deps.gitBranchName?.() ?? '',
    },
  };

  let total = 0;
  total += collectV4Sessions(deps, state, initAt, ctx);
  total += collectStudioProjectSessions(state, initAt, ctx);
  total += collectStudioDesktopSessions(state, initAt, ctx);
  total += collectLegacySessions(deps, state, initAt, ctx);

  writeState(telemetryDir, state);
  return total;
}

// ---------------------------------------------------------------------------
// 新版 v4（%APPDATA%\reasonix\projects\<dir>\sessions-v4\<hash>\events.frames）
// ---------------------------------------------------------------------------

/** reasonix 根目录：REASONIX_HOME 优先，其次 %APPDATA%\reasonix（Windows），最后 ~/.reasonix。 */
function reasonixHome() {
  if (process.env.REASONIX_HOME) return process.env.REASONIX_HOME;
  if (process.env.APPDATA) return join(process.env.APPDATA, 'reasonix');
  return join(homedir(), '.reasonix');
}

function collectV4Sessions(deps, state, initAt, ctx) {
  const home = reasonixHome();
  const projectsRoot = join(home, 'projects');
  if (!existsSync(projectsRoot)) return 0;

  const rootsByDir = readV4ProjectRegistry(home);
  let appendedTotal = 0;

  let dirs = [];
  try {
    dirs = readdirSync(projectsRoot, { withFileTypes: true }).filter((e) => e.isDirectory());
  } catch {
    return 0;
  }

  for (const entry of dirs) {
    const projectRoot = rootsByDir.get(entry.name.toLowerCase());
    if (!projectRoot) continue; // 注册表缺失 → 无法归属，跳过
    if (gitRootOf(projectRoot) !== ctx.repoRoot) continue; // 方案 A：非本仓库会话留给各自仓库

    const sessionsV4Dir = join(projectsRoot, entry.name, 'sessions-v4');
    if (!existsSync(sessionsV4Dir)) continue;
    let sessionDirs = [];
    try {
      sessionDirs = readdirSync(sessionsV4Dir, { withFileTypes: true }).filter((e) => e.isDirectory());
    } catch {
      continue;
    }
    for (const session of sessionDirs) {
      const sessionDir = join(sessionsV4Dir, session.name);
      const framesFile = join(sessionDir, 'events.frames');
      if (!existsSync(framesFile)) continue;
      try {
        if (statSync(framesFile).mtimeMs < initAt) continue; // 历史会话不回溯
      } catch {
        continue;
      }
      const lastSeqSeen = state.files[framesFile] ?? 0;
      const result = processV4File(framesFile, lastSeqSeen, ctx);
      if (result.lastCompleteSeq > lastSeqSeen) {
        state.files[framesFile] = result.lastCompleteSeq;
        appendedTotal += result.appended;
      }
    }
  }
  return appendedTotal;
}

/** 读 desktop-projects.json，构建"项目目录名(小写) → 项目根路径"映射（目录名 = root 把 : 和 \ 换成 -）。 */
function readV4ProjectRegistry(home) {
  const map = new Map();
  try {
    const cfg = JSON.parse(readFileSync(join(home, 'desktop-projects.json'), 'utf8'));
    for (const p of cfg.projects ?? []) {
      if (p && typeof p.root === 'string' && p.root) {
        map.set(mangleProjectDir(p.root), p.root);
      }
    }
  } catch {
    // 无注册表 → v4 归属无权威来源，跳过
  }
  return map;
}

/** 项目根路径 → 项目目录名：把 : 与 \（含正斜杠形式的 git 输出）替换为 -（与 reasonix 实际目录名一致）。 */
function mangleProjectDir(root) {
  return root.replace(/[\\/:]/g, '-').toLowerCase();
}

/**
 * 解析 v4 会话的 events.frames，返回 message/complete 事件列表。
 * @returns {{seq:number, role:string, content:string, rawContent:string|null, createdAt:number|null, commitTs:number|null}[]}
 */
export function parseV4Frames(framesFile) {
  let buf;
  try {
    buf = readFileSync(framesFile);
  } catch {
    return [];
  }
  if (buf.indexOf(ZSTD_MAGIC) === -1) return [];
  const text = decompressFrames(buf);
  if (!text) return [];

  const events = [];
  let commitTs = null;
  for (const chunk of text.split(V4_RECORD_SPLIT)) {
    if (!chunk.trim()) continue;
    let rec;
    try {
      rec = JSON.parse(chunk);
    } catch {
      continue;
    }
    if (rec.recordType === 'batch/begin') {
      const t = rec.createdAt;
      commitTs = t && t !== '0001-01-01T00:00:00Z' ? Date.parse(t) : commitTs;
      continue;
    }
    if (rec.recordType !== 'batch/event' || !rec.event) continue;
    if (rec.event.kind !== 'message/complete') continue;
    let payload;
    try {
      payload = JSON.parse(Buffer.from(rec.event.payload, 'base64').toString('utf8'));
    } catch {
      continue;
    }
    const msg = payload?.message;
    if (!msg || typeof msg.role !== 'string') continue;
    events.push({
      seq: rec.event.seq,
      role: msg.role,
      content: typeof msg.content === 'string' ? msg.content : '',
      rawContent: typeof msg.raw_content === 'string' && msg.raw_content.trim() ? msg.raw_content : null,
      createdAt: typeof msg.createdAt === 'number' && Number.isFinite(msg.createdAt) ? msg.createdAt : null,
      commitTs,
    });
  }
  return events;
}

/** 跳过 RX4F 帧头，按 ZSTD 魔数切帧解压并拼接（连续 JSON 无换行）。 */
function decompressFrames(buf) {
  if (typeof zlib.zstdDecompressSync !== 'function') return null; // Node <22.15
  const positions = [];
  let idx = 0;
  while (idx !== -1 && idx < buf.length) {
    const n = buf.indexOf(ZSTD_MAGIC, idx);
    if (n === -1) break;
    positions.push(n);
    idx = n + 4;
  }
  if (!positions.length) return null;
  let out = '';
  for (let i = 0; i < positions.length; i++) {
    const start = positions[i];
    const end = i + 1 < positions.length ? positions[i + 1] : buf.length;
    try {
      out += zlib.zstdDecompressSync(buf.subarray(start, end)).toString('utf8');
    } catch {
      return null; // 帧损坏 → 放弃该文件（水位不推进，下次重试）
    }
  }
  return out;
}

/**
 * 处理单个 v4 会话：按轮次提取，只收已完结轮次。
 * @returns {{lastCompleteSeq:number, appended:number}}
 */
function processV4File(framesFile, lastSeqSeen, ctx) {
  const fileMtime = safeMtime(framesFile);
  const events = parseV4Frames(framesFile);
  if (!events.length) return { lastCompleteSeq: lastSeqSeen, appended: 0 };

  let turnUser = null;
  let turnUserCreatedAt = null;
  let turnAssistant = null;
  let lastAssistantSeq = 0;
  let lastAssistantCommitTs = null;
  let appended = 0;
  let lastCompleteSeq = lastSeqSeen;
  const settled = Date.now() - fileMtime > SETTLE_MS;

  // 是否真实用户输入：带 raw_content（host 注入的 <session-context>/<workspace> 无此字段）
  const isRealUser = (e) => e.role === 'user' && e.rawContent;

  const finalize = (nextSeq) => {
    if (turnUser && turnAssistant) {
      if (lastAssistantSeq > lastSeqSeen) {
        if (!ctx.skipAppends) {
          ctx.appendEvent({
            event_id: ctx.eventId(),
            user_input_text: turnUser,
            ai_response_text: turnAssistant,
            generate_time: lastAssistantCommitTs ?? turnUserCreatedAt ?? fileMtime,
            ...ctx.identity,
            agent_tool: 'reasonix',
            source_session_id: ctx.sourceSessionId({ session_id: framesFile }, null),
            ai_provider: null,
            ai_model: null,
            requirement_id: null,
            feature_name: null,
            trace_id: null,
            trace_source: null,
            report_time: null,
          });
          appended += 1;
        }
      }
      lastCompleteSeq = Math.max(lastCompleteSeq, lastAssistantSeq);
    }
    turnUser = null;
    turnUserCreatedAt = null;
    turnAssistant = null;
    lastAssistantSeq = 0;
    lastAssistantCommitTs = null;
  };

  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    if (e.seq <= lastSeqSeen) continue;
    if (isRealUser(e)) {
      if (turnUser) finalize(e.seq);
      turnUser = e.rawContent;
      turnUserCreatedAt = e.createdAt;
    } else if (e.role === 'assistant') {
      const text = textOf(e.content);
      if (text) {
        turnAssistant = text;
        lastAssistantSeq = e.seq;
        lastAssistantCommitTs = e.commitTs;
      }
    }
    // tool / 注入 user / 空 assistant 消息忽略
  }

  // 末尾轮次：文件已静默（会话基本结束）才收，否则下次再收（水位不动）
  if (settled) finalize(events[events.length - 1].seq);

  return { lastCompleteSeq, appended };
}

// ---------------------------------------------------------------------------
// Reasonix STUDIO 项目会话（%APPDATA%\reasonix\projects\<dir>\sessions\<session>.jsonl）
// ---------------------------------------------------------------------------

/** STUDIO 在真实用户输入前拼接的 host 注入块标签；剥离后才是可上报的 user 文本。 */
const STUDIO_INJECTED_TAGS = [
  'workspace',
  'current-date',
  'session-scratch',
  'available-skills',
  'project-instructions',
  'memory-recall',
  'reasoning-language',
  'response-language',
  'execution-policy',
  'session-context',
  'capability-route',
  'interrupted-turn-recovery',
  'background-jobs',
  'skill-pin',
  'attached-images',
  'mid-turn-notice',
  'system-reminder',
];

const STUDIO_AUX_FILE = /\.(events|wire|adjudication)\.jsonl$|\.superseded\.jsonl\.bak$/;

/** 去掉 host 注入块，返回真实用户输入；纯注入消息返回空串。 */
export function stripStudioInjectedContext(content) {
  let text = typeof content === 'string' ? content : '';
  for (const tag of STUDIO_INJECTED_TAGS) {
    const block = new RegExp(`<${tag}(?:\\s[^>]*)?>[\\s\\S]*?<\\/${tag}>`, 'g');
    text = text.replace(block, '');
  }
  // host 的方括号系统通知（不是用户输入）
  text = text.replace(/\[Mid-turn notice from the host runtime;[\s\S]*?\]/g, '');
  return text.trim();
}

function collectStudioProjectSessions(state, initAt, ctx) {
  const home = reasonixHome();
  const projectsRoot = join(home, 'projects');
  if (!existsSync(projectsRoot)) return 0;

  const rootsByDir = readV4ProjectRegistry(home);
  if (!rootsByDir.size) return 0;

  let dirs = [];
  try {
    dirs = readdirSync(projectsRoot, { withFileTypes: true }).filter((e) => e.isDirectory());
  } catch {
    return 0;
  }

  let appendedTotal = 0;
  for (const entry of dirs) {
    const projectRoot = rootsByDir.get(entry.name.toLowerCase());
    if (!projectRoot) continue; // 注册表缺失 → 无法归属，跳过
    if (gitRootOf(projectRoot) !== ctx.repoRoot) continue; // 方案 A：非本仓库会话留给各自仓库

    const sessionsDir = join(projectsRoot, entry.name, 'sessions');
    if (!existsSync(sessionsDir)) continue;
    let files = [];
    try {
      files = readdirSync(sessionsDir, { withFileTypes: true }).filter((e) => e.isFile());
    } catch {
      continue;
    }

    for (const fileEntry of files) {
      const name = fileEntry.name;
      if (name.startsWith('.') || !name.endsWith('.jsonl') || STUDIO_AUX_FILE.test(name)) continue;
      const file = join(sessionsDir, name);
      try {
        if (statSync(file).mtimeMs < initAt) continue; // 历史会话不回溯
      } catch {
        continue;
      }
      const meta = readStudioSessionMeta(file);
      if (meta?.imported_from) continue; // v4 迁移副本：已由 v4 通道收集，避免重复上报

      const lastTurnSeen = state.files[file] ?? 0;
      const result = processStudioSessionFile(file, meta, lastTurnSeen, ctx);
      if (result.lastCompleteTurn > lastTurnSeen) {
        state.files[file] = result.lastCompleteTurn;
        appendedTotal += result.appended;
      }
    }
  }
  return appendedTotal;
}

function readStudioSessionMeta(file) {
  try {
    return JSON.parse(readFileSync(file + '.meta', 'utf8'));
  } catch {
    return null;
  }
}

/** 读同名 .events.jsonl 的增量记录，返回"消息索引 → 时间戳(ms)"映射；缺失/损坏返回空表。 */
function parseStudioMessageTimes(eventsFile) {
  const times = new Map();
  if (!existsSync(eventsFile)) return times;
  try {
    for (const line of readFileSync(eventsFile, 'utf8').split('\n')) {
      if (!line.trim()) continue;
      let event;
      try {
        event = JSON.parse(line);
      } catch {
        continue;
      }
      if (!Array.isArray(event?.messages)) continue;
      const start = event.type === 'append' && Number.isInteger(event.message_index) ? event.message_index : 0;
      const ts = typeof event.created_at === 'string' ? Date.parse(event.created_at) : NaN;
      if (!Number.isFinite(ts)) continue;
      for (let i = 0; i < event.messages.length; i++) times.set(start + i, ts);
    }
  } catch {
    // 事件流不可读 → 时间戳退回 meta / 文件 mtime
  }
  return times;
}

/**
 * 处理单个 STUDIO 项目会话：优先用 `.ckpt/turn-N.json` 的干净 `prompt` + `msgIndex` 切轮，
 * 从同名 jsonl 取该轮最后一条 assistant 文本；缺少 ckpt 的旧会话回退到 jsonl 注入剥离。
 * 末轮若仍在 `meta.in_flight_turn` 中则留待下次（文件静默超过 SETTLE_MS 视为已结束）。
 * @returns {{lastCompleteTurn:number, appended:number}}
 */
function processStudioSessionFile(file, meta, lastTurnSeen, ctx) {
  const turns = readStudioTurns(file.replace(/\.jsonl$/, '.ckpt'));
  if (!turns.length) return processStudioJsonlFallback(file, meta, lastTurnSeen, ctx);
  const messages = readStudioMessages(file);
  if (!messages.length) return { lastCompleteTurn: lastTurnSeen, appended: 0 };
  return processStudioTurns(file, meta, turns, messages, lastTurnSeen, ctx);
}

/** 读 `<session>.ckpt/turn-*.json`，按 turn 升序；缺失/损坏返回空数组。 */
function readStudioTurns(ckptDir) {
  const turns = [];
  if (!existsSync(ckptDir)) return turns;
  try {
    for (const entry of readdirSync(ckptDir, { withFileTypes: true })) {
      if (!entry.isFile() || !/^turn-\d+\.json$/.test(entry.name)) continue;
      try {
        const turn = JSON.parse(readFileSync(join(ckptDir, entry.name), 'utf8'));
        if (Number.isInteger(turn?.turn)) turns.push(turn);
      } catch {
        // 跳过损坏的单个 checkpoint
      }
    }
  } catch {
    return turns;
  }
  return turns.sort((a, b) => a.turn - b.turn);
}

/** 读 jsonl 消息数组（只保留有效消息对象，索引与 turn.msgIndex 对齐）。 */
function readStudioMessages(file) {
  const messages = [];
  try {
    for (const line of readFileSync(file, 'utf8').split('\n')) {
      if (!line.trim()) continue;
      try {
        const msg = JSON.parse(line);
        if (msg && typeof msg.role === 'string') messages.push(msg);
      } catch {
        // 跳过单行坏数据
      }
    }
  } catch {
    // 文件不可读 → 空数组
  }
  return messages;
}

/** 区间 [start, end) 内最后一条有文本的 assistant 回复；无则 null。 */
function lastAssistantTextInRange(messages, start, end) {
  const limit = Math.min(end, messages.length);
  for (let i = limit - 1; i >= start; i--) {
    const msg = messages[i];
    if (msg?.role === 'assistant') {
      const text = textOf(msg.content);
      if (text) return text;
    }
  }
  return null;
}

/**
 * 用 ckpt turn 文件切轮：`turn.prompt` 是 host 剥离后的真实输入，`turn.msgIndex` 指向 jsonl
 * 中该轮 user 消息；assistant 回复取 (msgIndex, 下一轮 msgIndex) 内最后一条文本。
 */
function processStudioTurns(file, meta, turns, messages, lastTurnSeen, ctx) {
  const fileMtime = safeMtime(file);
  const inFlightStart = Number.isInteger(meta?.in_flight_turn?.start_message_index)
    ? meta.in_flight_turn.start_message_index
    : null;
  const settled = Date.now() - fileMtime > SETTLE_MS;
  const model = typeof meta?.model === 'string' ? meta.model : null;

  let appended = 0;
  let lastCompleteTurn = lastTurnSeen;

  for (let i = 0; i < turns.length; i++) {
    const turn = turns[i];
    const turnIndex = turn.turn + 1; // turn 文件 0-based；水位用 1-based 轮次号
    if (turnIndex <= lastTurnSeen) continue;

    const start = Number.isInteger(turn.msgIndex) ? turn.msgIndex : -1;
    if (start < 0 || start >= messages.length) continue;

    // 进行中的末轮：先不推进水位，等 assistant 落盘 / 会话结束
    const isLast = i === turns.length - 1;
    const inFlight = inFlightStart !== null && start >= inFlightStart;
    if (isLast && inFlight && !settled) continue;

    const prompt = typeof turn.prompt === 'string' ? turn.prompt.trim() : '';
    const next = turns[i + 1];
    const end = next && Number.isInteger(next.msgIndex) && next.msgIndex > start ? next.msgIndex : messages.length;
    const assistant = lastAssistantTextInRange(messages, start + 1, end);

    // 无真实输入/无最终回复的轮次不产生事件，但推进水位避免下次重复扫描
    if (!prompt || !assistant) {
      lastCompleteTurn = Math.max(lastCompleteTurn, turnIndex);
      continue;
    }

    const ts = typeof turn.time === 'string' ? Date.parse(turn.time) : NaN;
    if (!ctx.skipAppends) {
      ctx.appendEvent({
        event_id: ctx.eventId(),
        user_input_text: prompt,
        ai_response_text: assistant,
        generate_time: Number.isFinite(ts) ? ts : fileMtime,
        ...ctx.identity,
        agent_tool: 'reasonix',
        source_session_id: ctx.sourceSessionId({ session_id: file }, null),
        ai_provider: null,
        ai_model: model,
        requirement_id: null,
        feature_name: null,
        trace_id: null,
        trace_source: null,
        report_time: null,
      });
      appended += 1;
    }
    lastCompleteTurn = Math.max(lastCompleteTurn, turnIndex);
  }
  return { lastCompleteTurn, appended };
}

/** 回退路径（无 ckpt 的旧会话）：按 jsonl 真实 user 消息切轮，需剥离 host 注入块。 */
function processStudioJsonlFallback(file, meta, lastTurnSeen, ctx) {
  const fileMtime = safeMtime(file);
  const messageTimes = parseStudioMessageTimes(file.replace(/\.jsonl$/, '.events.jsonl'));
  const inFlightStart = Number.isInteger(meta?.in_flight_turn?.start_message_index)
    ? meta.in_flight_turn.start_message_index
    : null;
  const settled = Date.now() - fileMtime > SETTLE_MS;

  let lines;
  try {
    lines = readFileSync(file, 'utf8').split('\n');
  } catch {
    return { lastCompleteTurn: lastTurnSeen, appended: 0 };
  }

  let messageIndex = -1;
  let turnIndex = 0;
  let turnUser = null;
  let turnStartIndex = -1;
  let turnAssistant = null;
  let lastAssistantIndex = -1;
  let appended = 0;
  let lastCompleteTurn = lastTurnSeen;

  const finalize = () => {
    if (turnUser && turnAssistant && turnIndex > lastTurnSeen) {
      const ts =
        messageTimes.get(lastAssistantIndex) ??
        (typeof meta?.updated_at === 'string' ? Date.parse(meta.updated_at) : NaN) ??
        fileMtime;
      if (!ctx.skipAppends) {
        ctx.appendEvent({
          event_id: ctx.eventId(),
          user_input_text: turnUser,
          ai_response_text: turnAssistant,
          generate_time: Number.isFinite(ts) ? ts : fileMtime,
          ...ctx.identity,
          agent_tool: 'reasonix',
          source_session_id: ctx.sourceSessionId({ session_id: file }, null),
          ai_provider: null,
          ai_model: typeof meta?.model === 'string' ? meta.model : null,
          requirement_id: null,
          feature_name: null,
          trace_id: null,
          trace_source: null,
          report_time: null,
        });
        appended += 1;
      }
      lastCompleteTurn = Math.max(lastCompleteTurn, turnIndex);
    }
    turnUser = null;
    turnStartIndex = -1;
    turnAssistant = null;
    lastAssistantIndex = -1;
  };

  for (const line of lines) {
    if (!line.trim()) continue;
    let msg;
    try {
      msg = JSON.parse(line);
    } catch {
      continue;
    }
    messageIndex += 1;
    if (!msg || typeof msg.role !== 'string') continue;

    if (msg.role === 'user') {
      const text = stripStudioInjectedContext(msg.content);
      if (!text) continue; // 纯注入上下文（workspace / skills / policy 等），不是真实输入
      if (turnUser) finalize(); // 出现下一条真实 user → 上一轮已完结
      turnIndex += 1;
      turnUser = text;
      turnStartIndex = messageIndex;
      turnAssistant = null;
      lastAssistantIndex = -1;
    } else if (msg.role === 'assistant') {
      const text = textOf(msg.content);
      if (text) {
        turnAssistant = text;
        lastAssistantIndex = messageIndex;
      }
    }
    // tool / system 消息不含对话文本，忽略
  }

  // 末轮：meta 标记仍在进行中则等下次；文件静默（会话已停）时兜底收集，防止卡死轮次永远不上报
  const lastRoundInFlight = inFlightStart !== null && turnStartIndex >= inFlightStart;
  if (!lastRoundInFlight || settled) finalize();

  return { lastCompleteTurn, appended };
}

// ---------------------------------------------------------------------------
// Reasonix STUDIO 桌面会话（%APPDATA%\reasonix\desktop-sessions-v5\by-id\<id>\events.frames）
// ---------------------------------------------------------------------------

function collectStudioDesktopSessions(state, initAt, ctx) {
  const byId = join(reasonixHome(), 'desktop-sessions-v5', 'by-id');
  if (!existsSync(byId)) return 0;

  let sessionDirs = [];
  try {
    sessionDirs = readdirSync(byId, { withFileTypes: true }).filter(
      (e) => e.isDirectory() && !e.name.startsWith('.'),
    );
  } catch {
    return 0;
  }

  let appendedTotal = 0;
  // 同一批会话通常来自少量 cwd：缓存 git 根，避免每个会话都起一次 git 子进程
  const gitRootCache = new Map();
  const v4Ids = v4SessionIds();
  for (const entry of sessionDirs) {
    const sessionDir = join(byId, entry.name);
    const framesFile = join(sessionDir, 'events.frames');
    if (!existsSync(framesFile)) continue;
    try {
      if (statSync(framesFile).mtimeMs < initAt) continue; // 历史会话不回溯
    } catch {
      continue;
    }

    // 归属：header.json 的 cwd（STUDIO 桌面会话不按项目目录存放）
    let header;
    try {
      header = JSON.parse(readFileSync(join(sessionDir, 'header.json'), 'utf8'));
    } catch {
      continue;
    }
    const cwd = typeof header?.cwd === 'string' ? header.cwd : '';
    if (!cwd) continue;
    const cwdKey = cwd.toLowerCase();
    if (!gitRootCache.has(cwdKey)) gitRootCache.set(cwdKey, gitRootOf(cwd));
    if (gitRootCache.get(cwdKey) !== ctx.repoRoot) continue;
    if (v4Ids.has(entry.name)) continue; // 同一会话已由 v4 通道收集

    const lastSeqSeen = state.files[framesFile] ?? 0;
    const result = processV4File(framesFile, lastSeqSeen, ctx);
    if (result.lastCompleteSeq > lastSeqSeen) {
      state.files[framesFile] = result.lastCompleteSeq;
      appendedTotal += result.appended;
    }
  }
  return appendedTotal;
}

/** 所有项目 sessions-v4 下的会话 id 集合（用于 desktop-sessions-v5 双通道去重）。 */
function v4SessionIds() {
  const ids = new Set();
  const projectsRoot = join(reasonixHome(), 'projects');
  if (!existsSync(projectsRoot)) return ids;
  try {
    for (const project of readdirSync(projectsRoot, { withFileTypes: true })) {
      if (!project.isDirectory()) continue;
      const sessionsV4 = join(projectsRoot, project.name, 'sessions-v4');
      if (!existsSync(sessionsV4)) continue;
      for (const session of readdirSync(sessionsV4, { withFileTypes: true })) {
        if (session.isDirectory() && !session.name.startsWith('.')) ids.add(session.name);
      }
    }
  } catch {
    // 目录不可读 → 不做双通道去重
  }
  return ids;
}

// ---------------------------------------------------------------------------
// 旧版明文（~/.reasonix/sessions/code-<项目>[-<时间戳>].jsonl，OpenAI chat 格式）
// ---------------------------------------------------------------------------

function collectLegacySessions(deps, state, initAt, ctx) {
  const sessionsRoot = legacySessionsRoot();
  if (!sessionsRoot) return 0;

  const registry = legacyProjectRegistry(sessionsRoot);
  let files = [];
  try {
    files = readdirSync(sessionsRoot, { withFileTypes: true })
      .filter((e) => e.isFile() && /\.jsonl$/.test(e.name))
      .filter((e) => !e.name.endsWith('.events.jsonl') && !e.name.endsWith('.meta.json')
        && !e.name.endsWith('.plan.json') && !e.name.endsWith('.bak'));
  } catch {
    return 0;
  }

  let appendedTotal = 0;
  for (const entry of files) {
    const file = join(sessionsRoot, entry.name);
    try {
      if (statSync(file).mtimeMs < initAt) continue; // 历史会话不回溯收集
    } catch {
      continue;
    }
    const token = sessionToken(entry.name);
    if (!token) continue; // acp-* 或无项目 token → 不可归属，跳过

    let projectRoot = registryRootForToken(token, registry);
    let gitRoot = projectRoot ? gitRootOf(projectRoot) : null;
    if (gitRoot !== ctx.repoRoot) {
      const fromTranscript = repoRootFromTranscript(file, ctx.repoRoot);
      if (fromTranscript) {
        projectRoot = fromTranscript;
        gitRoot = ctx.repoRoot;
      }
    }
    if (gitRoot !== ctx.repoRoot) continue;

    const lastTurnSeen = state.files[file] ?? 0;
    const result = processLegacyFile(file, lastTurnSeen, ctx);
    if (result.lastCompleteTurn > lastTurnSeen) {
      state.files[file] = result.lastCompleteTurn;
      appendedTotal += result.appended;
    }
  }
  return appendedTotal;
}

function legacySessionsRoot() {
  const root = join(reasonixHome(), 'sessions');
  return existsSync(root) ? root : null;
}

/**
 * 旧格式项目注册表：reasonix 接触过的项目根路径集合。
 * 数据源：① reasonixHome()/config.json 的 projects map；② 各项目 sessions/<路径改写>/checkpoints/index.json 的 rootDir。
 */
function legacyProjectRegistry(sessionsRoot) {
  const roots = new Set();
  const home = reasonixHome();
  try {
    const cfg = JSON.parse(readFileSync(join(home, 'config.json'), 'utf8'));
    if (cfg && typeof cfg.projects === 'object') {
      for (const p of Object.keys(cfg.projects)) {
        if (/^[A-Za-z]:[\\/]/.test(p) || p.startsWith('/')) roots.add(p);
      }
    }
  } catch {
    // 无 config.json
  }
  try {
    for (const entry of readdirSync(sessionsRoot, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      try {
        const idx = JSON.parse(readFileSync(join(sessionsRoot, entry.name, 'checkpoints', 'index.json'), 'utf8'));
        for (const cp of idx ?? []) {
          if (cp && typeof cp.rootDir === 'string' && cp.rootDir) roots.add(cp.rootDir);
        }
      } catch {
        // 无该项目的 checkpoints
      }
    }
  } catch {
    // 无项目子目录
  }
  return [...roots];
}

/** 从旧格式会话文件名解析项目 token：code-<token>[-<14位时间戳>].jsonl → token；acp-* 等返回 null。 */
export function sessionToken(filename) {
  if (!filename.startsWith('code-')) return null;
  let rest = filename.slice('code-'.length).replace(/\.jsonl$/, '');
  rest = rest.replace(/-\d{14}$/, '');
  return rest || null;
}

/** 注册表中找 token 对应的项目根：basename 改写后相等，或完整路径改写后以 _<token> 结尾。
 *  改写规则与 reasonix 的文件名一致：非 [A-Za-z0-9-]（路径分隔符、点号等）替换为 _，连字符保留。 */
export function registryRootForToken(token, registry) {
  for (const root of registry) {
    if (basename(root).replace(/[^A-Za-z0-9-]/g, '_') === token) return root;
  }
  for (const root of registry) {
    if (root.replace(/[^A-Za-z0-9-]/g, '_').endsWith('_' + token)) return root;
  }
  return null;
}

/** 回退归属：扫描转录中出现的绝对路径，取 git 根 == 当前仓库者（防止注册表缺失时漏收本仓库会话）。 */
function repoRootFromTranscript(file, repoRoot) {
  try {
    const text = readFileSync(file, 'utf8');
    const absPath = /(?:[A-Za-z]:[\\/]|\/)[^\s"']+/g;
    let m;
    while ((m = absPath.exec(text)) !== null) {
      const dir = dirname(m[0]);
      try {
        if (gitRootOf(dir) === repoRoot) return dir;
      } catch {
        // 该路径不在 git 仓库内，试下一个
      }
    }
  } catch {
    // 读文件失败
  }
  return null;
}

/**
 * 处理单个旧格式会话文件：按 chat 消息提取轮次，只收"已完结"轮次（存在 model.final）。
 * @returns {{lastCompleteTurn:number, appended:number}}
 */
function processLegacyFile(file, lastTurnSeen, ctx) {
  const events = parseLegacyEvents(file);
  const fileMtime = safeMtime(file);
  let lines;
  try {
    lines = readFileSync(file, 'utf8').split('\n');
  } catch {
    return { lastCompleteTurn: lastTurnSeen, appended: 0 };
  }

  let turnIndex = 0; // 第几个 user 消息（1-based，对应 events.jsonl 的 turn）
  let turnUser = null;
  let turnAssistant = null;
  let appended = 0;
  let lastCompleteTurn = lastTurnSeen;

  const finalize = () => {
    // 已完结判定：有 user 文本 + assistant 文本；有 events 时还要求该 turn 存在 model.final
    const complete = turnUser && turnAssistant && (events ? hasTurnFinal(events, turnIndex) : true);
    if (complete && turnIndex > lastTurnSeen) {
      const finalEvent = lastTextFinal(events, turnIndex);
      const preciseTs = finalEvent ? Date.parse(finalEvent.ts) : NaN;
      if (!ctx.skipAppends) {
        ctx.appendEvent({
          event_id: ctx.eventId(),
          user_input_text: turnUser,
          ai_response_text: turnAssistant,
          generate_time: Number.isFinite(preciseTs) ? preciseTs : fileMtime,
          ...ctx.identity,
          agent_tool: 'reasonix',
          source_session_id: ctx.sourceSessionId({ session_id: file }, null),
          ai_provider: null,
          ai_model: modelOfTurn(events, turnIndex),
          requirement_id: null,
          feature_name: null,
          trace_id: null,
          trace_source: null,
          report_time: null,
        });
        appended += 1;
      }
      lastCompleteTurn = Math.max(lastCompleteTurn, turnIndex);
    }
    turnUser = null;
    turnAssistant = null;
  };

  for (const line of lines) {
    if (!line.trim()) continue;
    let msg;
    try {
      msg = JSON.parse(line);
    } catch {
      continue;
    }
    if (!msg || typeof msg.role !== 'string') continue;
    if (msg.role === 'user') {
      if (turnUser) finalize();
      turnIndex += 1;
      turnUser = typeof msg.content === 'string' ? msg.content : '';
    } else if (msg.role === 'assistant') {
      const text = textOf(msg.content);
      if (text) turnAssistant = text;
    }
    // tool 消息忽略（不含对话文本）
  }
  finalize();

  return { lastCompleteTurn, appended };
}

/** 读取同名 .events.jsonl，返回含 turn 的事件列表；缺失/损坏返回 null。 */
function parseLegacyEvents(file) {
  const eventsFile = file.replace(/\.jsonl$/, '.events.jsonl');
  if (!existsSync(eventsFile)) return null;
  const list = [];
  for (const line of readFileSync(eventsFile, 'utf8').split('\n')) {
    if (!line.trim()) continue;
    try {
      const e = JSON.parse(line);
      if (e && e.turn != null) list.push(e);
    } catch {
      // 跳过单行坏数据
    }
  }
  return list;
}

function hasTurnFinal(events, turn) {
  return events.some((e) => e.type === 'model.final' && e.turn === turn);
}

/** 该轮最后一条"非空 content"的 model.final（作为时间戳来源）；无则退回该轮任意 model.final。 */
function lastTextFinal(events, turn) {
  const finals = events.filter((e) => e.type === 'model.final' && e.turn === turn);
  if (!finals.length) return null;
  const textFinals = finals.filter((f) => f.content && String(f.content).trim());
  return (textFinals.length ? textFinals : finals).at(-1);
}

/** 该轮 model.turn.started 里声明的模型名（如 deepseek-v4-pro）。 */
function modelOfTurn(events, turn) {
  const started = events?.find((e) => e.type === 'model.turn.started' && e.turn === turn);
  return started?.model ?? null;
}

// ---------------------------------------------------------------------------
// 通用
// ---------------------------------------------------------------------------

function gitRootOf(dir) {
  try {
    return (
      execFileSync('git', ['-C', dir, 'rev-parse', '--show-toplevel'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        windowsHide: true, // 父进程被隐藏时，git 子进程不另开控制台窗口
      }).trim() || null
    );
  } catch {
    return null;
  }
}

export function textOf(content) {
  if (typeof content === 'string') return content.trim() ? content : null;
  if (Array.isArray(content)) {
    const blocks = content.filter((b) => b?.type === 'text' && typeof b.text === 'string' && b.text.trim());
    return blocks.length ? blocks.map((b) => b.text).join('\n') : null;
  }
  return null;
}

function safeMtime(file) {
  try {
    return statSync(file).mtimeMs;
  } catch {
    return Date.now();
  }
}

function readState(telemetryDir) {
  const stateFile = join(telemetryDir, 'cache', 'reasonix-collect-state.json');
  try {
    const parsed = JSON.parse(readFileSync(stateFile, 'utf8'));
    if (parsed && typeof parsed === 'object') {
      return { files: parsed.files ?? {}, initAt: parsed.initAt ?? 0 };
    }
  } catch {
    // 无状态文件
  }
  return { files: {}, initAt: 0 };
}

/** hook 活跃 marker 落点（与 wrapper 共享的唯一定义；wrapper 触碰、收集器读取）。 */
export function hookActiveMarkerPath(telemetryDir) {
  return join(telemetryDir, 'cache', 'reasonix-hook-active.json');
}

/** 触碰 hook 活跃 marker（wrapper 在 UserPromptSubmit / Stop 时调用）。 */
export function touchHookActiveMarker(telemetryDir) {
  try {
    mkdirSync(join(telemetryDir, 'cache'), { recursive: true });
    writeFileSync(hookActiveMarkerPath(telemetryDir), JSON.stringify({ updated: Date.now() }));
  } catch {
    // silent
  }
}

/** hook 链路是否活跃（marker 在新鲜窗口内）——活跃时收集器只推进水位不追加。 */
export function isHookActive(telemetryDir) {
  try {
    const data = JSON.parse(readFileSync(hookActiveMarkerPath(telemetryDir), 'utf8'));
    const updated = typeof data?.updated === 'number' ? data.updated : 0;
    return Date.now() - updated < HOOK_ACTIVE_WINDOW_MS;
  } catch {
    return false;
  }
}

function writeState(telemetryDir, state) {
  const stateFile = join(telemetryDir, 'cache', 'reasonix-collect-state.json');
  try {
    mkdirSync(join(telemetryDir, 'cache'), { recursive: true });
    writeFileSync(stateFile, JSON.stringify({ ...state, initAt: state.initAt || Date.now() }));
  } catch {
    // silent
  }
}
